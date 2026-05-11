package pay.conflux.backend.risk.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.springframework.expression.Expression;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.risk.engine.BlacklistCache;
import pay.conflux.backend.risk.engine.CompiledRule;
import pay.conflux.backend.risk.engine.CompiledRuleCache;
import pay.conflux.backend.risk.engine.SafeSpelEvaluator;
import pay.conflux.backend.risk.engine.VelocityCounter;
import pay.conflux.backend.risk.engine.VelocityDimension;
import pay.conflux.backend.risk.entity.BlacklistType;
import pay.conflux.backend.risk.entity.RiskEvaluation;
import pay.conflux.backend.risk.entity.RiskRule;
import pay.conflux.backend.risk.entity.RuleAction;
import pay.conflux.backend.risk.repository.MerchantRiskProfileRepository;
import pay.conflux.backend.risk.repository.RiskEvaluationRepository;
import pay.conflux.backend.risk.usecase.impl.EvaluateTransactionUseCaseImpl;

/**
 * Property-based tests (jqwik) for the risk evaluation engine.
 *
 * <p>Three core invariants:
 *
 * <ol>
 *   <li><b>Score monotonicity:</b> adding a matching rule with positive scoreWeight can only
 *       increase or hold totalScore — never decrease it.
 *   <li><b>Decision precedence:</b> if any matching rule has action=BLOCK, the final decision is
 *       BLOCK regardless of totalScore.
 *   <li><b>Audit row exists:</b> for any TransactionContext, exactly one {@link RiskEvaluation} row
 *       is persisted per {@code evaluate()} call.
 * </ol>
 */
@Label("Risk Evaluation Properties")
class RiskPropertyTest {

  private static final int FLAG_THRESHOLD = 50;

  // ── Score monotonicity ─────────────────────────────────────────────────

  @Property(tries = 100)
  @Label("Score monotonicity: adding a matching rule never decreases totalScore")
  void scoreMonotonicity(
      @ForAll("transactionContext") TransactionContext ctx,
      @ForAll("ruleList") List<@From("riskRule") RiskRule> rules,
      @ForAll @IntRange(min = 1, max = 100) int extraScoreWeight) {

    // Build engine with the given rules
    EvaluateTransactionUseCaseImpl engine1 = buildEngine(rules);
    RiskDecision result1 = engine1.execute(ctx);

    // Add one more matching rule with positive scoreWeight
    RiskRule extra = new RiskRule();
    extra.setId(UUID.randomUUID());
    extra.setName("extra-rule");
    extra.setExpression("true");
    extra.setScoreWeight(extraScoreWeight);
    extra.setAction(RuleAction.FLAG);
    extra.setActive(true);

    List<RiskRule> extended = new ArrayList<>(rules);
    extended.add(extra);

    EvaluateTransactionUseCaseImpl engine2 = buildEngine(extended);
    RiskDecision result2 = engine2.execute(ctx);

    // Score can only increase or hold (it holds only if extra rule didn't match,
    // but in our setup all rules match because expression="true" from the mock)
    assertTrue(
        result2.score() >= result1.score(),
        String.format(
            "Score monotonicity violated: original=%d, after adding rule=%d (weight=%d)",
            result1.score(), result2.score(), extraScoreWeight));
  }

  // ── Decision precedence ────────────────────────────────────────────────

  @Property(tries = 100)
  @Label("Decision precedence: BLOCK rule in the set forces BLOCK outcome")
  void decisionPrecedence(
      @ForAll("transactionContext") TransactionContext ctx,
      @ForAll("ruleList") List<@From("riskRule") RiskRule> rules) {

    // Ensure at least one BLOCK rule is present
    RiskRule blockRule = new RiskRule();
    blockRule.setId(UUID.randomUUID());
    blockRule.setName("mandatory-block");
    blockRule.setExpression("true");
    blockRule.setScoreWeight(1);
    blockRule.setAction(RuleAction.BLOCK);
    blockRule.setActive(true);

    List<RiskRule> withBlock = new ArrayList<>(rules);
    withBlock.add(blockRule);

    EvaluateTransactionUseCaseImpl engine = buildEngine(withBlock);
    RiskDecision result = engine.execute(ctx);

    assertEquals(
        RiskDecision.Action.BLOCK,
        result.action(),
        "Decision must be BLOCK when any matching rule has action=BLOCK, but was "
            + result.action());
  }

  // ── Audit row exists ───────────────────────────────────────────────────

  @Property(tries = 100)
  @Label("Audit row: exactly one RiskEvaluation persisted per evaluate() call")
  void auditRowExists(@ForAll("transactionContext") TransactionContext ctx) {
    AtomicInteger saveCount = new AtomicInteger(0);

    // Mock setup
    CompiledRuleCache compiledRuleCache = mock(CompiledRuleCache.class);
    when(compiledRuleCache.snapshot()).thenReturn(List.of());

    BlacklistCache blacklistCache = mock(BlacklistCache.class);
    when(blacklistCache.isBlacklisted(any(BlacklistType.class), anyString())).thenReturn(false);

    VelocityCounter velocityCounter = mock(VelocityCounter.class);
    when(velocityCounter.incrementAndGet(any(UUID.class), any(VelocityDimension.class), anyLong()))
        .thenReturn(1L);

    SafeSpelEvaluator safeSpelEvaluator = mock(SafeSpelEvaluator.class);

    MerchantRiskProfileRepository profileRepo = mock(MerchantRiskProfileRepository.class);
    when(profileRepo.findByMerchantId(any(UUID.class))).thenReturn(Optional.empty());

    RiskEvaluationRepository evalRepo = mock(RiskEvaluationRepository.class);
    when(evalRepo.save(any(RiskEvaluation.class)))
        .thenAnswer(
            inv -> {
              saveCount.incrementAndGet();
              return inv.getArgument(0);
            });

    EvaluateTransactionUseCaseImpl engine =
        new EvaluateTransactionUseCaseImpl(
            compiledRuleCache,
            blacklistCache,
            velocityCounter,
            safeSpelEvaluator,
            evalRepo,
            profileRepo,
            new com.fasterxml.jackson.databind.ObjectMapper(),
            FLAG_THRESHOLD);

    engine.execute(ctx);

    assertEquals(
        1, saveCount.get(), "Exactly one RiskEvaluation row must be persisted per evaluate() call");
  }

  @Property(tries = 100)
  @Label("Non-null decision for every TransactionContext")
  void alwaysReturnsDecision(@ForAll("transactionContext") TransactionContext ctx) {
    EvaluateTransactionUseCaseImpl engine = buildEngine(List.of());
    RiskDecision decision = engine.execute(ctx);
    assertNotNull(decision);
    assertNotNull(decision.action());
  }

  // ── Arbitraries ────────────────────────────────────────────────────────

  @Provide
  Arbitrary<TransactionContext> transactionContext() {
    Arbitrary<UUID> merchantId = Arbitraries.create(UUID::randomUUID);
    Arbitrary<Money> amount =
        Arbitraries.of(
            Money.of(BigDecimal.valueOf(100), "BDT"),
            Money.of(BigDecimal.valueOf(5000), "BDT"),
            Money.of(BigDecimal.valueOf(15000), "BDT"),
            Money.of(BigDecimal.valueOf(5), "BDT"),
            Money.of(BigDecimal.valueOf(100000), "BDT"));
    Arbitrary<String> vendor = Arbitraries.of("BKASH", "NAGAD", "ROCKET", "UPAY");
    Arbitrary<String> phone =
        Arbitraries.of("+8801712345678", "+8801812345678", "+919876543210", "+8801512345678");
    Arbitrary<String> email =
        Arbitraries.of(
            "user@gmail.com", "test@mailinator.com", "user@yahoo.com", "corp@company.com");
    Arbitrary<String> ip = Arbitraries.of("192.168.1.1", "10.0.0.1", "203.0.113.45", "172.16.0.1");

    return Combinators.combine(merchantId, amount, vendor, phone, email, ip)
        .as(
            (mid, amt, ven, ph, em, ipAddr) ->
                new TransactionContext(mid, amt, ven, ph, em, ipAddr, Map.of()));
  }

  @Provide
  Arbitrary<RiskRule> riskRule() {
    Arbitrary<Integer> scoreWeight = Arbitraries.integers().between(1, 100);
    Arbitrary<RuleAction> action =
        Arbitraries.of(
            RuleAction.FLAG,
            RuleAction.ALLOW); // No BLOCK here to avoid early return in monotonicity

    return Combinators.combine(scoreWeight, action)
        .as(
            (sw, act) -> {
              RiskRule rule = new RiskRule();
              rule.setId(UUID.randomUUID());
              rule.setName("rule-" + UUID.randomUUID().toString().substring(0, 8));
              rule.setExpression("true");
              rule.setScoreWeight(sw);
              rule.setAction(act);
              rule.setActive(true);
              return rule;
            });
  }

  @Provide
  Arbitrary<List<RiskRule>> ruleList() {
    return riskRule().list().ofMinSize(1).ofMaxSize(10);
  }

  // ── Engine builder ─────────────────────────────────────────────────────

  private EvaluateTransactionUseCaseImpl buildEngine(List<RiskRule> rules) {
    CompiledRuleCache compiledRuleCache = mock(CompiledRuleCache.class);
    List<CompiledRule> compiledRules = new ArrayList<>();
    for (RiskRule rule : rules) {
      compiledRules.add(new CompiledRule(rule, mock(Expression.class)));
    }
    when(compiledRuleCache.snapshot()).thenReturn(compiledRules);

    BlacklistCache blacklistCache = mock(BlacklistCache.class);
    when(blacklistCache.isBlacklisted(any(BlacklistType.class), anyString())).thenReturn(false);

    VelocityCounter velocityCounter = mock(VelocityCounter.class);
    when(velocityCounter.incrementAndGet(any(UUID.class), any(VelocityDimension.class), anyLong()))
        .thenReturn(1L);

    SafeSpelEvaluator safeSpelEvaluator = mock(SafeSpelEvaluator.class);
    // Make all rules evaluate to true for property tests
    when(safeSpelEvaluator.evaluate(any(Expression.class), any(TransactionContext.class)))
        .thenReturn(true);

    MerchantRiskProfileRepository profileRepo = mock(MerchantRiskProfileRepository.class);
    when(profileRepo.findByMerchantId(any(UUID.class))).thenReturn(Optional.empty());

    RiskEvaluationRepository evalRepo = mock(RiskEvaluationRepository.class);
    when(evalRepo.save(any(RiskEvaluation.class))).thenAnswer(i -> i.getArgument(0));

    return new EvaluateTransactionUseCaseImpl(
        compiledRuleCache,
        blacklistCache,
        velocityCounter,
        safeSpelEvaluator,
        evalRepo,
        profileRepo,
        new com.fasterxml.jackson.databind.ObjectMapper(),
        FLAG_THRESHOLD);
  }
}
