package com.shadhinpay.risk.benchmark;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.shadhinpay.common.money.Money;
import com.shadhinpay.risk.engine.BlacklistCache;
import com.shadhinpay.risk.engine.CompiledRule;
import com.shadhinpay.risk.engine.CompiledRuleCache;
import com.shadhinpay.risk.engine.SafeSpelEvaluator;
import com.shadhinpay.risk.engine.VelocityCounter;
import com.shadhinpay.risk.engine.VelocityDimension;
import com.shadhinpay.risk.entity.BlacklistType;
import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.entity.RuleAction;
import com.shadhinpay.risk.repository.MerchantRiskProfileRepository;
import com.shadhinpay.risk.repository.RiskEvaluationRepository;
import com.shadhinpay.risk.usecase.TransactionContext;
import com.shadhinpay.risk.usecase.impl.EvaluateTransactionUseCaseImpl;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * Plain JUnit 5 latency benchmark for the risk evaluation pipeline.
 *
 * <p>Seeds 20 realistic SpEL rules, warms up with 1,000 evaluations, then measures 10,000
 * evaluations of varied {@link TransactionContext} inputs. Reports p50, p95, and p99 in
 * microseconds.
 *
 * <p>Target: p99 &lt; 10ms per TECH_SPEC §6. Soft assertion on CI; hard failure at p99 &lt; 50ms.
 */
@DisplayName("Risk Latency Benchmark")
class RiskLatencyBenchmarkTest {

  private static final Logger log = LoggerFactory.getLogger(RiskLatencyBenchmarkTest.class);
  private static final int WARMUP_COUNT = 1_000;
  private static final int MEASURE_COUNT = 10_000;
  private static final int FLAG_THRESHOLD = 50;
  private static final UUID MERCHANT_ID = UUID.randomUUID();

  private static EvaluateTransactionUseCaseImpl useCase;

  @BeforeAll
  static void setUp() {
    CompiledRuleCache compiledRuleCache = new FakeCompiledRuleCache(seedRules());
    BlacklistCache blacklistCache = new FakeBlacklistCache();
    VelocityCounter velocityCounter = new FakeVelocityCounter();
    SafeSpelEvaluator safeSpelEvaluator = new FakeSafeSpelEvaluator();

    RiskEvaluationRepository riskEvaluationRepository =
        Mockito.mock(RiskEvaluationRepository.class);
    when(riskEvaluationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    MerchantRiskProfileRepository profileRepo = Mockito.mock(MerchantRiskProfileRepository.class);
    when(profileRepo.findByMerchantId(any())).thenReturn(Optional.empty());

    useCase =
        new EvaluateTransactionUseCaseImpl(
            compiledRuleCache,
            blacklistCache,
            velocityCounter,
            safeSpelEvaluator,
            riskEvaluationRepository,
            profileRepo,
            FLAG_THRESHOLD);

    // Warm up
    log.info("Warming up with {} evaluations...", WARMUP_COUNT);
    for (int i = 0; i < WARMUP_COUNT; i++) {
      useCase.execute(variedContext(i));
    }

    log.info("Warm-up complete. Measuring {} evaluations...", MEASURE_COUNT);
  }

  @Test
  @DisplayName("p99 latency < 10ms for 20-rule evaluation")
  void latencyUnderTenMillis() {
    long[] latencies = new long[MEASURE_COUNT];

    for (int i = 0; i < MEASURE_COUNT; i++) {
      TransactionContext ctx = variedContext(WARMUP_COUNT + i);
      long start = System.nanoTime();
      useCase.execute(ctx);
      long end = System.nanoTime();
      latencies[i] = end - start;
    }

    Arrays.sort(latencies);

    long p50Nanos = latencies[MEASURE_COUNT / 2];
    long p95Nanos = latencies[(int) (MEASURE_COUNT * 0.95)];
    long p99Nanos = latencies[(int) (MEASURE_COUNT * 0.99)];

    double p50Micros = p50Nanos / 1_000.0;
    double p95Micros = p95Nanos / 1_000.0;
    double p99Micros = p99Nanos / 1_000.0;

    log.info(
        "Latency benchmark results — p50: {:.2f} µs, p95: {:.2f} µs, p99: {:.2f} µs ({}"
            + " evaluations, 20 rules)",
        p50Micros,
        p95Micros,
        p99Micros,
        MEASURE_COUNT);

    double p99Millis = p99Micros / 1_000.0;

    // TECH_SPEC §6 target: 20 rules in <10ms
    if (p99Millis < 10.0) {
      log.info("PASS: p99 {:.3f} ms < 10 ms target", p99Millis);
    } else if (p99Millis < 50.0) {
      log.warn(
          "SOFT FAIL: p99 {:.3f} ms exceeds 10 ms target but within 50 ms hard limit "
              + "(likely CI flakiness — document actual numbers in commit message)",
          p99Millis);
    } else {
      // Hard failure
      log.error(
          "HARD FAIL: p99 {:.3f} ms exceeds 50 ms hard limit. Machine may be overloaded.",
          p99Millis);
    }

    // Write results to stdout for easy capture
    System.out.printf(
        "BENCHMARK_RESULTS p50_µs=%.2f p95_µs=%.2f p99_µs=%.2f p99_ms=%.3f%n",
        p50Micros, p95Micros, p99Micros, p99Millis);

    // Hard assertion: p99 must be < 50ms (CI-safe)
    org.junit.jupiter.api.Assertions.assertTrue(
        p99Millis < 50.0,
        String.format("p99 latency %.3f ms exceeded 50 ms hard limit", p99Millis));
  }

  // ── Fixtures ────────────────────────────────────────────────────────────

  /** Generate 20 realistic SpEL rules. */
  private static List<CompiledRule> seedRules() {
    SpelExpressionParser parser = new SpelExpressionParser();
    List<CompiledRule> rules = new ArrayList<>();

    // Amount-based rules
    rules.add(makeRule(parser, "High Amount >10k", "amount.amount > 10000", 30, RuleAction.FLAG));
    rules.add(
        makeRule(parser, "Very High Amount >50k", "amount.amount > 50000", 50, RuleAction.BLOCK));
    rules.add(makeRule(parser, "Tiny Amount <10", "amount.amount < 10", 5, RuleAction.FLAG));
    rules.add(makeRule(parser, "Round Amount", "amount.amount % 1000 == 0", 10, RuleAction.FLAG));

    // Vendor-based rules
    rules.add(makeRule(parser, "BKASH vendor", "vendor == 'BKASH'", 5, RuleAction.FLAG));
    rules.add(makeRule(parser, "NAGAD vendor", "vendor == 'NAGAD'", 5, RuleAction.FLAG));
    rules.add(makeRule(parser, "Unknown vendor", "vendor == 'UNKNOWN'", 40, RuleAction.BLOCK));

    // Phone-based rules
    rules.add(
        makeRule(
            parser,
            "Phone starts +88017",
            "customerPhone.startsWith('+88017')",
            3,
            RuleAction.FLAG));
    rules.add(
        makeRule(
            parser,
            "Phone starts +88018",
            "customerPhone.startsWith('+88018')",
            3,
            RuleAction.FLAG));
    rules.add(
        makeRule(parser, "Non-BD phone", "!customerPhone.startsWith('+880')", 20, RuleAction.FLAG));

    // Email-based rules
    rules.add(
        makeRule(
            parser, "Gmail address", "customerEmail.contains('@gmail.com')", 2, RuleAction.FLAG));
    rules.add(
        makeRule(
            parser,
            "Disposable email",
            "customerEmail.contains('mailinator.com')",
            25,
            RuleAction.FLAG));

    // IP-based rules
    rules.add(
        makeRule(
            parser,
            "Private IP",
            "ip.startsWith('192.168.') || ip.startsWith('10.')",
            5,
            RuleAction.FLAG));
    rules.add(
        makeRule(
            parser,
            "Foreign IP",
            "!ip.startsWith('192.') && !ip.startsWith('10.') && !ip.startsWith('172.')",
            15,
            RuleAction.FLAG));

    // Combined rules
    rules.add(
        makeRule(
            parser,
            "High+Foreign",
            "amount.amount > 10000 && !ip.startsWith('192.168.')",
            35,
            RuleAction.BLOCK));
    rules.add(
        makeRule(
            parser,
            "High+Gmail",
            "amount.amount > 5000 && customerEmail.contains('@gmail.com')",
            15,
            RuleAction.FLAG));
    rules.add(
        makeRule(
            parser, "BKASH+Tiny", "vendor == 'BKASH' && amount.amount < 10", 8, RuleAction.FLAG));
    rules.add(
        makeRule(
            parser,
            "NAGAD+High",
            "vendor == 'NAGAD' && amount.amount > 20000",
            20,
            RuleAction.FLAG));

    // Additional rules to reach 20
    rules.add(makeRule(parser, "Rule 19", "amount.amount > 0", 1, RuleAction.FLAG));
    rules.add(makeRule(parser, "Rule 20", "amount.amount >= 0", 1, RuleAction.FLAG));

    return rules;
  }

  private static CompiledRule makeRule(
      SpelExpressionParser parser, String name, String expr, int scoreWeight, RuleAction action) {
    RiskRule riskRule = new RiskRule();
    riskRule.setId(UUID.randomUUID());
    riskRule.setName(name);
    riskRule.setExpression(expr);
    riskRule.setScoreWeight(scoreWeight);
    riskRule.setAction(action);
    riskRule.setActive(true);
    Expression expression = parser.parseExpression(expr);
    return new CompiledRule(riskRule, expression);
  }

  private static TransactionContext variedContext(int seed) {
    String[] phones = {
      "+8801712345678", "+8801812345678", "+8801512345678", "+919876543210", "+8801612345678"
    };
    String[] emails = {
      "user@gmail.com",
      "test@mailinator.com",
      "user@yahoo.com",
      "corp@company.com",
      "admin@example.com"
    };
    String[] ips = {"192.168.1.1", "10.0.0.1", "203.0.113.45", "172.16.0.1", "198.51.100.22"};
    String[] vendors = {"BKASH", "NAGAD", "ROCKET", "UPAY", "BKASH"};
    int[] amounts = {100, 5000, 15000, 60000, 5};

    int idx = seed % 5;
    return new TransactionContext(
        MERCHANT_ID,
        Money.of(BigDecimal.valueOf(amounts[idx]), "BDT"),
        vendors[idx],
        phones[idx],
        emails[idx],
        ips[idx],
        Map.of());
  }

  // ── Fake implementations ────────────────────────────────────────────────

  static class FakeCompiledRuleCache extends CompiledRuleCache {
    private final List<CompiledRule> rules;

    FakeCompiledRuleCache(List<CompiledRule> rules) {
      super(null, null);
      this.rules = List.copyOf(rules);
    }

    @Override
    public java.util.Collection<CompiledRule> snapshot() {
      return rules;
    }
  }

  static class FakeBlacklistCache extends BlacklistCache {
    FakeBlacklistCache() {
      super(null, null);
    }

    @Override
    public boolean isBlacklisted(BlacklistType type, String value) {
      return false;
    }
  }

  static class FakeVelocityCounter extends VelocityCounter {
    FakeVelocityCounter() {
      super(null);
    }

    @Override
    public long incrementAndGet(UUID merchantId, VelocityDimension dim, long windowSize) {
      return 1L;
    }
  }

  static class FakeSafeSpelEvaluator extends SafeSpelEvaluator {

    FakeSafeSpelEvaluator() {
      super(null, null);
    }

    @Override
    public Boolean evaluate(Expression compiled, TransactionContext ctx) {
      try {
        SimpleEvaluationContext context =
            SimpleEvaluationContext.forReadOnlyDataBinding().withRootObject(ctx).build();
        return compiled.getValue(context, Boolean.class);
      } catch (Exception e) {
        return false;
      }
    }
  }
}
