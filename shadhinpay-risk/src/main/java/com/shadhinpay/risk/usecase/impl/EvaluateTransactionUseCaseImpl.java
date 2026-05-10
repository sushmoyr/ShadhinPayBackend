package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.risk.engine.BlacklistCache;
import com.shadhinpay.risk.engine.CompiledRule;
import com.shadhinpay.risk.engine.CompiledRuleCache;
import com.shadhinpay.risk.engine.SafeSpelEvaluator;
import com.shadhinpay.risk.engine.VelocityCounter;
import com.shadhinpay.risk.engine.VelocityDimension;
import com.shadhinpay.risk.entity.BlacklistType;
import com.shadhinpay.risk.entity.MerchantRiskProfile;
import com.shadhinpay.risk.entity.RiskEvaluation;
import com.shadhinpay.risk.entity.RuleAction;
import com.shadhinpay.risk.entity.TrustLevel;
import com.shadhinpay.risk.repository.MerchantRiskProfileRepository;
import com.shadhinpay.risk.repository.RiskEvaluationRepository;
import com.shadhinpay.risk.usecase.EvaluateTransactionUseCase;
import com.shadhinpay.risk.usecase.RiskDecision;
import com.shadhinpay.risk.usecase.TransactionContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Synchronous risk evaluation engine implementing the canonical algorithm from TECH_SPEC §4.1.
 *
 * <p>Evaluation order (cheapest first):
 *
 * <ol>
 *   <li>Blacklist lookup (Redis O(1))
 *   <li>Velocity counters (Redis INCR)
 *   <li>SpEL rule evaluation (Caffeine-cached compiled expressions)
 *   <li>Decision: BLOCK (immediate) > FLAG (score ≥ threshold) > ALLOW
 * </ol>
 *
 * <p><b>Fail-CLOSED:</b> any uncaught exception is mapped to {@code BLOCK} with reason {@code
 * RISK_ENGINE_FAILURE}. The engine never fails open.
 *
 * <p>A {@link RiskEvaluation} audit row is always persisted, including for ALLOW decisions.
 */
@Slf4j
@UseCase
public class EvaluateTransactionUseCaseImpl implements EvaluateTransactionUseCase {

  private final CompiledRuleCache compiledRuleCache;
  private final BlacklistCache blacklistCache;
  private final VelocityCounter velocityCounter;
  private final SafeSpelEvaluator safeSpelEvaluator;
  private final RiskEvaluationRepository riskEvaluationRepository;
  private final MerchantRiskProfileRepository merchantRiskProfileRepository;
  private final int flagThreshold;

  public EvaluateTransactionUseCaseImpl(
      CompiledRuleCache compiledRuleCache,
      BlacklistCache blacklistCache,
      VelocityCounter velocityCounter,
      SafeSpelEvaluator safeSpelEvaluator,
      RiskEvaluationRepository riskEvaluationRepository,
      MerchantRiskProfileRepository merchantRiskProfileRepository,
      int flagThreshold) {
    this.compiledRuleCache = compiledRuleCache;
    this.blacklistCache = blacklistCache;
    this.velocityCounter = velocityCounter;
    this.safeSpelEvaluator = safeSpelEvaluator;
    this.riskEvaluationRepository = riskEvaluationRepository;
    this.merchantRiskProfileRepository = merchantRiskProfileRepository;
    this.flagThreshold = flagThreshold;
  }

  @Override
  public RiskDecision execute(TransactionContext ctx) {
    try {
      return doEvaluate(ctx);
    } catch (Exception e) {
      log.error("Risk engine failure — failing CLOSED", e);
      RiskDecision decision =
          new RiskDecision(RiskDecision.Action.BLOCK, 0, List.of(), "RISK_ENGINE_FAILURE");
      try {
        persistEvaluation(ctx, decision, Instant.now());
      } catch (Exception persistEx) {
        log.error("Failed to persist RISK_ENGINE_FAILURE evaluation", persistEx);
      }
      return decision;
    }
  }

  // ── Step 1: Blacklist ────────────────────────────────────────────────────

  private RiskDecision checkBlacklist(TransactionContext ctx) {
    // PHONE
    if (blacklistCache.isBlacklisted(BlacklistType.PHONE, ctx.customerPhone())) {
      return blocked("Blacklist hit: PHONE");
    }
    // EMAIL
    if (blacklistCache.isBlacklisted(BlacklistType.EMAIL, ctx.customerEmail())) {
      return blocked("Blacklist hit: EMAIL");
    }
    // IP
    if (blacklistCache.isBlacklisted(BlacklistType.IP, ctx.ip())) {
      return blocked("Blacklist hit: IP");
    }
    // MERCHANT
    if (blacklistCache.isBlacklisted(BlacklistType.MERCHANT, ctx.merchantId().toString())) {
      return blocked("Blacklist hit: MERCHANT");
    }
    return null;
  }

  // ── Step 2: Velocity ─────────────────────────────────────────────────────

  private RiskDecision checkVelocity(TransactionContext ctx) {
    MerchantRiskProfile profile =
        merchantRiskProfileRepository.findByMerchantId(ctx.merchantId()).orElse(null);

    Map<String, Long> limits = resolveLimits(profile);

    // PER_MERCHANT minute
    long count =
        velocityCounter.incrementAndGet(ctx.merchantId(), VelocityDimension.PER_MERCHANT, 60);
    if (count > limits.getOrDefault("per_merchant_minute", 5L)) {
      return blocked("Velocity: PER_MERCHANT/minute exceeded");
    }
    // PER_MERCHANT hour
    count = velocityCounter.incrementAndGet(ctx.merchantId(), VelocityDimension.PER_MERCHANT, 3600);
    if (count > limits.getOrDefault("per_merchant_hour", 50L)) {
      return blocked("Velocity: PER_MERCHANT/hour exceeded");
    }
    // PER_MERCHANT day
    count =
        velocityCounter.incrementAndGet(ctx.merchantId(), VelocityDimension.PER_MERCHANT, 86400);
    if (count > limits.getOrDefault("per_merchant_day", 200L)) {
      return blocked("Velocity: PER_MERCHANT/day exceeded");
    }
    // PER_IP minute
    UUID ipKey = UUID.nameUUIDFromBytes(ctx.ip().getBytes());
    count = velocityCounter.incrementAndGet(ipKey, VelocityDimension.PER_IP, 60);
    if (count > limits.getOrDefault("per_ip_minute", 3L)) {
      return blocked("Velocity: PER_IP/minute exceeded");
    }
    // PER_PHONE minute
    UUID phoneKey = UUID.nameUUIDFromBytes(ctx.customerPhone().getBytes());
    count = velocityCounter.incrementAndGet(phoneKey, VelocityDimension.PER_PHONE, 60);
    if (count > limits.getOrDefault("per_phone_minute", 3L)) {
      return blocked("Velocity: PER_PHONE/minute exceeded");
    }

    return null;
  }

  // ── Step 3 + 4: Rule evaluation + Decision ───────────────────────────────

  private RiskDecision evaluateRules(TransactionContext ctx) {
    int totalScore = 0;
    List<UUID> triggeredIds = new ArrayList<>();

    for (CompiledRule compiled : compiledRuleCache.snapshot()) {
      Boolean match = safeSpelEvaluator.evaluate(compiled.expression(), ctx);
      if (Boolean.TRUE.equals(match)) {
        totalScore += compiled.rule().getScoreWeight();
        triggeredIds.add(compiled.rule().getId());

        if (compiled.rule().getAction() == RuleAction.BLOCK) {
          return new RiskDecision(
              RiskDecision.Action.BLOCK,
              totalScore,
              triggeredIds,
              "Rule BLOCK: " + compiled.rule().getName());
        }
      }
    }

    RiskDecision.Action action;
    String reason;
    if (totalScore >= flagThreshold) {
      action = RiskDecision.Action.FLAG;
      reason = String.format("Score %d >= threshold %d", totalScore, flagThreshold);
    } else {
      action = RiskDecision.Action.ALLOW;
      reason = String.format("Score %d < threshold %d", totalScore, flagThreshold);
    }
    return new RiskDecision(action, totalScore, triggeredIds, reason);
  }

  // ── Main evaluation pipeline ─────────────────────────────────────────────

  private RiskDecision doEvaluate(TransactionContext ctx) {
    Instant now = Instant.now();

    // Step 1: Blacklist
    RiskDecision blacklistDecision = checkBlacklist(ctx);
    if (blacklistDecision != null) {
      persistEvaluation(ctx, blacklistDecision, now);
      return blacklistDecision;
    }

    // Step 2: Velocity
    RiskDecision velocityDecision = checkVelocity(ctx);
    if (velocityDecision != null) {
      persistEvaluation(ctx, velocityDecision, now);
      return velocityDecision;
    }

    // Step 3 + 4: Rule evaluation + Decision
    RiskDecision decision = evaluateRules(ctx);
    persistEvaluation(ctx, decision, now);
    return decision;
  }

  // ── Persistence ──────────────────────────────────────────────────────────

  private void persistEvaluation(
      TransactionContext ctx, RiskDecision decision, Instant evaluatedAt) {
    RiskEvaluation eval = new RiskEvaluation();
    eval.setTransactionId(UUID.randomUUID()); // placeholder; Wave B will supply the real tx id
    eval.setMerchantId(ctx.merchantId());
    eval.setTotalScore(decision.score());
    eval.setDecision(decision.action());
    eval.setTriggeredRuleIds(new ArrayList<>(decision.triggeredRuleIds()));
    eval.setReason(decision.reason());
    eval.setEvaluatedAt(evaluatedAt);
    riskEvaluationRepository.save(eval);
  }

  // ── Velocity helpers ─────────────────────────────────────────────────────

  private RiskDecision blocked(String reason) {
    return new RiskDecision(RiskDecision.Action.BLOCK, 0, List.of(), reason);
  }

  private Map<String, Long> resolveLimits(MerchantRiskProfile profile) {
    if (profile == null) {
      return defaultLimits(TrustLevel.NEW);
    }
    return defaultLimits(profile.getTrustLevel());
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static Map<String, Long> defaultLimits(TrustLevel level) {
    return switch (level) {
      case NEW ->
          Map.of(
              "per_merchant_minute", 5L,
              "per_merchant_hour", 50L,
              "per_merchant_day", 200L,
              "per_ip_minute", 3L,
              "per_phone_minute", 3L);
      case VERIFIED ->
          Map.of(
              "per_merchant_minute", 20L,
              "per_merchant_hour", 200L,
              "per_merchant_day", 1000L,
              "per_ip_minute", 10L,
              "per_phone_minute", 10L);
      case TRUSTED ->
          Map.of(
              "per_merchant_minute", 100L,
              "per_merchant_hour", 1000L,
              "per_merchant_day", 5000L,
              "per_ip_minute", 50L,
              "per_phone_minute", 50L);
      case VIP ->
          Map.of(
              "per_merchant_minute", 500L,
              "per_merchant_hour", 5000L,
              "per_merchant_day", 20000L,
              "per_ip_minute", 200L,
              "per_phone_minute", 200L);
    };
  }
}
