package com.shadhinpay.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.shadhinpay.common.money.Money;
import com.shadhinpay.risk.engine.BlacklistCache;
import com.shadhinpay.risk.engine.CompiledRule;
import com.shadhinpay.risk.engine.CompiledRuleCache;
import com.shadhinpay.risk.engine.SafeSpelEvaluator;
import com.shadhinpay.risk.engine.VelocityCounter;
import com.shadhinpay.risk.engine.VelocityDimension;
import com.shadhinpay.risk.entity.BlacklistType;
import com.shadhinpay.risk.entity.RiskEvaluation;
import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.entity.RuleAction;
import com.shadhinpay.risk.repository.MerchantRiskProfileRepository;
import com.shadhinpay.risk.repository.RiskEvaluationRepository;
import com.shadhinpay.risk.usecase.RiskDecision;
import com.shadhinpay.risk.usecase.TransactionContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;

@DisplayName("EvaluateTransactionUseCaseImpl")
class EvaluateTransactionUseCaseImplTest {

  private static final int FLAG_THRESHOLD = 50;
  private static final UUID MERCHANT_ID = UUID.randomUUID();

  private CompiledRuleCache compiledRuleCache;
  private BlacklistCache blacklistCache;
  private VelocityCounter velocityCounter;
  private SafeSpelEvaluator safeSpelEvaluator;
  private RiskEvaluationRepository riskEvaluationRepository;
  private MerchantRiskProfileRepository merchantRiskProfileRepository;
  private EvaluateTransactionUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    compiledRuleCache = mock(CompiledRuleCache.class);
    blacklistCache = mock(BlacklistCache.class);
    velocityCounter = mock(VelocityCounter.class);
    safeSpelEvaluator = mock(SafeSpelEvaluator.class);
    riskEvaluationRepository = mock(RiskEvaluationRepository.class);
    merchantRiskProfileRepository = mock(MerchantRiskProfileRepository.class);
    useCase =
        new EvaluateTransactionUseCaseImpl(
            compiledRuleCache,
            blacklistCache,
            velocityCounter,
            safeSpelEvaluator,
            riskEvaluationRepository,
            merchantRiskProfileRepository,
            FLAG_THRESHOLD);

    // Default: nothing is blacklisted
    when(blacklistCache.isBlacklisted(any(BlacklistType.class), anyString())).thenReturn(false);

    // Default: velocity counters return 1 (within limits)
    when(velocityCounter.incrementAndGet(any(UUID.class), any(VelocityDimension.class), anyLong()))
        .thenReturn(1L);

    // Default: no merchant profile (falls back to NEW limits)
    when(merchantRiskProfileRepository.findByMerchantId(any(UUID.class)))
        .thenReturn(Optional.empty());

    // Default: no compiled rules
    when(compiledRuleCache.snapshot()).thenReturn(List.of());

    // Default: persist returns something
    when(riskEvaluationRepository.save(any(RiskEvaluation.class)))
        .thenAnswer(i -> i.getArgument(0));
  }

  private static TransactionContext makeContext() {
    return new TransactionContext(
        MERCHANT_ID,
        Money.of(BigDecimal.valueOf(100), "BDT"),
        "BKASH",
        "+8801712345678",
        "customer@example.com",
        "192.168.1.1",
        Map.of());
  }

  private static TransactionContext makeContext(String phone, String email, String ip) {
    return new TransactionContext(
        MERCHANT_ID, Money.of(BigDecimal.valueOf(100), "BDT"), "BKASH", phone, email, ip, Map.of());
  }

  // ── Blacklist tests ──────────────────────────────────────────────────────

  @Nested
  @DisplayName("Blacklist")
  class BlacklistTests {

    @Test
    @DisplayName("BLOCK on blacklisted phone")
    void blockOnBlacklistedPhone() {
      when(blacklistCache.isBlacklisted(BlacklistType.PHONE, "+8801712345678")).thenReturn(true);

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.BLOCK, result.action());
      assertTrue(result.reason().contains("Blacklist hit: PHONE"));
      verify(riskEvaluationRepository).save(any(RiskEvaluation.class));
    }

    @Test
    @DisplayName("BLOCK on blacklisted email")
    void blockOnBlacklistedEmail() {
      when(blacklistCache.isBlacklisted(BlacklistType.EMAIL, "customer@example.com"))
          .thenReturn(true);

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.BLOCK, result.action());
      assertTrue(result.reason().contains("Blacklist hit: EMAIL"));
    }

    @Test
    @DisplayName("BLOCK on blacklisted IP")
    void blockOnBlacklistedIp() {
      when(blacklistCache.isBlacklisted(BlacklistType.IP, "192.168.1.1")).thenReturn(true);

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.BLOCK, result.action());
      assertTrue(result.reason().contains("Blacklist hit: IP"));
    }

    @Test
    @DisplayName("BLOCK on blacklisted merchant")
    void blockOnBlacklistedMerchant() {
      when(blacklistCache.isBlacklisted(BlacklistType.MERCHANT, MERCHANT_ID.toString()))
          .thenReturn(true);

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.BLOCK, result.action());
      assertTrue(result.reason().contains("Blacklist hit: MERCHANT"));
    }

    @Test
    @DisplayName("proceeds when nothing is blacklisted")
    void proceedsWhenClean() {
      RiskDecision result = useCase.execute(makeContext());

      assertNotNull(result);
      verify(compiledRuleCache).snapshot();
    }
  }

  // ── Velocity tests ───────────────────────────────────────────────────────

  @Nested
  @DisplayName("Velocity")
  class VelocityTests {

    @Test
    @DisplayName("BLOCK on PER_MERCHANT/minute exceeded")
    void blockOnPerMerchantMinuteExceeded() {
      when(velocityCounter.incrementAndGet(
              eq(MERCHANT_ID), eq(VelocityDimension.PER_MERCHANT), eq(60L)))
          .thenReturn(10L); // > 5 (NEW limit)

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.BLOCK, result.action());
      assertTrue(result.reason().contains("PER_MERCHANT/minute"));
    }

    @Test
    @DisplayName("BLOCK on PER_MERCHANT/hour exceeded")
    void blockOnPerMerchantHourExceeded() {
      when(velocityCounter.incrementAndGet(
              eq(MERCHANT_ID), eq(VelocityDimension.PER_MERCHANT), eq(60L)))
          .thenReturn(1L);
      when(velocityCounter.incrementAndGet(
              eq(MERCHANT_ID), eq(VelocityDimension.PER_MERCHANT), eq(3600L)))
          .thenReturn(100L); // > 50

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.BLOCK, result.action());
      assertTrue(result.reason().contains("PER_MERCHANT/hour"));
    }

    @Test
    @DisplayName("BLOCK on PER_IP/minute exceeded")
    void blockOnPerIpMinuteExceeded() {
      // Return 1 for all merchant checks, then exceed IP limit
      when(velocityCounter.incrementAndGet(any(UUID.class), eq(VelocityDimension.PER_IP), eq(60L)))
          .thenReturn(10L); // > 3

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.BLOCK, result.action());
      assertTrue(result.reason().contains("PER_IP/minute"));
    }

    @Test
    @DisplayName("BLOCK on PER_PHONE/minute exceeded")
    void blockOnPerPhoneMinuteExceeded() {
      // Only phone velocity exceeds
      when(velocityCounter.incrementAndGet(
              any(UUID.class), eq(VelocityDimension.PER_PHONE), eq(60L)))
          .thenReturn(10L); // > 3

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.BLOCK, result.action());
      assertTrue(result.reason().contains("PER_PHONE/minute"));
    }

    @Test
    @DisplayName("proceeds when velocity is within limits")
    void proceedsWhenVelocityOk() {
      // All velocity counters return 1 (within NEW limits of 5/50/200/3/3)
      RiskDecision result = useCase.execute(makeContext());

      // Should reach rule evaluation (even if no rules match)
      assertNotNull(result);
      verify(compiledRuleCache).snapshot();
    }
  }

  // ── Decision matrix tests ───────────────────────────────────────────────

  @Nested
  @DisplayName("Decision matrix")
  class DecisionMatrixTests {

    @Test
    @DisplayName("ALLOW when no rules match")
    void allowWhenNoRulesMatch() {
      when(compiledRuleCache.snapshot()).thenReturn(List.of());

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.ALLOW, result.action());
      assertEquals(0, result.score());
      verify(riskEvaluationRepository).save(any(RiskEvaluation.class));
    }

    @Test
    @DisplayName("ALLOW when total score below threshold")
    void allowWhenScoreBelowThreshold() {
      CompiledRule rule = compiledRule("low-score", 10, RuleAction.FLAG);
      when(compiledRuleCache.snapshot()).thenReturn(List.of(rule));
      when(safeSpelEvaluator.evaluate(any(Expression.class), any(TransactionContext.class)))
          .thenReturn(true);

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.ALLOW, result.action());
      assertEquals(10, result.score());
    }

    @Test
    @DisplayName("FLAG when total score reaches threshold")
    void flagWhenScoreReachesThreshold() {
      CompiledRule rule = compiledRule("high-score", FLAG_THRESHOLD, RuleAction.FLAG);
      when(compiledRuleCache.snapshot()).thenReturn(List.of(rule));
      when(safeSpelEvaluator.evaluate(any(Expression.class), any(TransactionContext.class)))
          .thenReturn(true);

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.FLAG, result.action());
      assertEquals(FLAG_THRESHOLD, result.score());
    }

    @Test
    @DisplayName("FLAG when total score exceeds threshold")
    void flagWhenScoreExceedsThreshold() {
      CompiledRule rule = compiledRule("high-score", FLAG_THRESHOLD + 10, RuleAction.FLAG);
      when(compiledRuleCache.snapshot()).thenReturn(List.of(rule));
      when(safeSpelEvaluator.evaluate(any(Expression.class), any(TransactionContext.class)))
          .thenReturn(true);

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.FLAG, result.action());
      assertEquals(FLAG_THRESHOLD + 10, result.score());
    }

    @Test
    @DisplayName("BLOCK when any matching rule has action=BLOCK (short-circuit)")
    void blockOnBlockRule() {
      CompiledRule allowRule = compiledRule("allow-rule", 10, RuleAction.FLAG);
      CompiledRule blockRule = compiledRule("block-rule", 5, RuleAction.BLOCK);

      when(compiledRuleCache.snapshot()).thenReturn(List.of(allowRule, blockRule));
      when(safeSpelEvaluator.evaluate(any(Expression.class), any(TransactionContext.class)))
          .thenReturn(true);

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.BLOCK, result.action());
      assertTrue(result.reason().contains("block-rule"));
      // Should short-circuit; only the first two rules are evaluated
      // (allowRule matched, then blockRule matched → BLOCK)
      assertEquals(15, result.score()); // 10 + 5
    }

    @Test
    @DisplayName("aggregated score from multiple matching rules")
    void aggregateScoreFromMultipleRules() {
      CompiledRule r1 = compiledRule("r1", 15, RuleAction.FLAG);
      CompiledRule r2 = compiledRule("r2", 20, RuleAction.FLAG);
      CompiledRule r3 = compiledRule("r3", 25, RuleAction.FLAG);

      when(compiledRuleCache.snapshot()).thenReturn(List.of(r1, r2, r3));
      when(safeSpelEvaluator.evaluate(any(Expression.class), any(TransactionContext.class)))
          .thenReturn(true);

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.FLAG, result.action());
      assertEquals(60, result.score()); // 15 + 20 + 25 = 60 >= 50
      assertEquals(3, result.triggeredRuleIds().size());
    }
  }

  // ── Threshold boundary tests ─────────────────────────────────────────────

  @Nested
  @DisplayName("Threshold boundary")
  class ThresholdBoundaryTests {

    @Test
    @DisplayName("score == threshold-1 → ALLOW")
    void scoreBelowThresholdIsAllow() {
      CompiledRule rule = compiledRule("borderline", FLAG_THRESHOLD - 1, RuleAction.FLAG);
      when(compiledRuleCache.snapshot()).thenReturn(List.of(rule));
      when(safeSpelEvaluator.evaluate(any(Expression.class), any(TransactionContext.class)))
          .thenReturn(true);

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.ALLOW, result.action());
    }

    @Test
    @DisplayName("score == threshold → FLAG")
    void scoreAtThresholdIsFlag() {
      CompiledRule rule = compiledRule("at-threshold", FLAG_THRESHOLD, RuleAction.FLAG);
      when(compiledRuleCache.snapshot()).thenReturn(List.of(rule));
      when(safeSpelEvaluator.evaluate(any(Expression.class), any(TransactionContext.class)))
          .thenReturn(true);

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.FLAG, result.action());
    }
  }

  // ── Fail-closed tests ──────────────────────────────────────────────────

  @Nested
  @DisplayName("Fail-closed")
  class FailClosedTests {

    @Test
    @DisplayName("RISK_ENGINE_FAILURE when CompiledRuleCache throws")
    void failClosedWhenCacheThrows() {
      when(compiledRuleCache.snapshot()).thenThrow(new RuntimeException("cache down"));

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.BLOCK, result.action());
      assertEquals("RISK_ENGINE_FAILURE", result.reason());
    }

    @Test
    @DisplayName("RISK_ENGINE_FAILURE when SafeSpelEvaluator throws unexpectedly")
    void failClosedWhenEvaluatorThrows() {
      CompiledRule rule = compiledRule("r1", 10, RuleAction.FLAG);
      when(compiledRuleCache.snapshot()).thenReturn(List.of(rule));
      when(safeSpelEvaluator.evaluate(any(Expression.class), any(TransactionContext.class)))
          .thenThrow(new RuntimeException("evaluation failure"));

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.BLOCK, result.action());
      assertEquals("RISK_ENGINE_FAILURE", result.reason());
    }

    @Test
    @DisplayName("RISK_ENGINE_FAILURE when repository throws")
    void failClosedWhenRepositoryThrows() {
      CompiledRule rule = compiledRule("r1", 10, RuleAction.FLAG);
      when(compiledRuleCache.snapshot()).thenReturn(List.of(rule));
      when(safeSpelEvaluator.evaluate(any(Expression.class), any(TransactionContext.class)))
          .thenReturn(true);
      when(riskEvaluationRepository.save(any(RiskEvaluation.class)))
          .thenThrow(new RuntimeException("db error"));

      RiskDecision result = useCase.execute(makeContext());

      assertEquals(RiskDecision.Action.BLOCK, result.action());
      assertEquals("RISK_ENGINE_FAILURE", result.reason());
    }
  }

  // ── Persistence tests ──────────────────────────────────────────────────

  @Nested
  @DisplayName("Persistence")
  class PersistenceTests {

    @Test
    @DisplayName("persists RiskEvaluation on ALLOW")
    void persistsOnAllow() {
      useCase.execute(makeContext());
      verify(riskEvaluationRepository).save(any(RiskEvaluation.class));
    }

    @Test
    @DisplayName("persists RiskEvaluation on FLAG")
    void persistsOnFlag() {
      CompiledRule rule = compiledRule("high", FLAG_THRESHOLD, RuleAction.FLAG);
      when(compiledRuleCache.snapshot()).thenReturn(List.of(rule));
      when(safeSpelEvaluator.evaluate(any(Expression.class), any(TransactionContext.class)))
          .thenReturn(true);

      useCase.execute(makeContext());
      verify(riskEvaluationRepository).save(any(RiskEvaluation.class));
    }

    @Test
    @DisplayName("persists RiskEvaluation on BLOCK")
    void persistsOnBlock() {
      when(blacklistCache.isBlacklisted(BlacklistType.PHONE, "+8801712345678")).thenReturn(true);

      useCase.execute(makeContext());
      verify(riskEvaluationRepository).save(any(RiskEvaluation.class));
    }
  }

  // ── Helper ──────────────────────────────────────────────────────────────

  private static CompiledRule compiledRule(String name, int scoreWeight, RuleAction action) {
    RiskRule rule = new RiskRule();
    rule.setId(UUID.randomUUID());
    rule.setName(name);
    rule.setExpression("amount.amount > 0");
    rule.setScoreWeight(scoreWeight);
    rule.setAction(action);
    rule.setActive(true);
    return new CompiledRule(rule, mock(Expression.class));
  }
}
