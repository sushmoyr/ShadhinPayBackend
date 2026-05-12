package pay.conflux.backend.risk.benchmark;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.risk.engine.BlacklistCache;
import pay.conflux.backend.risk.engine.CompiledRule;
import pay.conflux.backend.risk.engine.CompiledRuleCache;
import pay.conflux.backend.risk.engine.SafeSpelEvaluator;
import pay.conflux.backend.risk.engine.VelocityCounter;
import pay.conflux.backend.risk.engine.VelocityDimension;
import pay.conflux.backend.risk.entity.RiskRule;
import pay.conflux.backend.risk.enums.BlacklistType;
import pay.conflux.backend.risk.enums.RuleAction;
import pay.conflux.backend.risk.repository.MerchantRiskProfileRepository;
import pay.conflux.backend.risk.repository.RiskEvaluationRepository;
import pay.conflux.backend.risk.usecase.TransactionContext;
import pay.conflux.backend.risk.usecase.impl.EvaluateTransactionUseCaseImpl;

/**
 * Plain JUnit 5 latency benchmark for the risk evaluation pipeline.
 *
 * <p>Seeds 20 realistic SpEL rules, warms up with 1,000 evaluations, then measures 10,000
 * evaluations of varied {@link TransactionContext} inputs. Reports p50, p95, and p99 in
 * microseconds.
 *
 * <p>This benchmark exercises the production {@link SafeSpelEvaluator} — including its
 * per-evaluation {@link ExecutorService} time-boxing — so the numbers reflect the real production
 * code path.
 *
 * <p>Target (TECH_SPEC §6): p99 &lt; 10 ms. Hard CI ceiling: p99 &lt; 50 ms.
 */
@DisplayName("Risk Latency Benchmark")
class RiskLatencyBenchmarkTest {

  private static final Logger log = LoggerFactory.getLogger(RiskLatencyBenchmarkTest.class);
  private static final int WARMUP_COUNT = 1_000;
  private static final int MEASURE_COUNT = 10_000;
  private static final int FLAG_THRESHOLD = 50;
  private static final UUID MERCHANT_ID = UUID.randomUUID();

  private static EvaluateTransactionUseCaseImpl useCase;
  private static ExecutorService spelExecutor;

  @BeforeAll
  static void setUp() {
    spelExecutor = Executors.newFixedThreadPool(8);
    SafeSpelEvaluator realEvaluator = new SafeSpelEvaluator(spelExecutor);

    CompiledRuleCache compiledRuleCache = new FakeCompiledRuleCache(seedRules());
    BlacklistCache blacklistCache = new FakeBlacklistCache();
    VelocityCounter velocityCounter = new FakeVelocityCounter();

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
            realEvaluator,
            riskEvaluationRepository,
            profileRepo,
            new ObjectMapper(),
            FLAG_THRESHOLD);

    log.info("Warming up with {} evaluations...", WARMUP_COUNT);
    for (int i = 0; i < WARMUP_COUNT; i++) {
      useCase.execute(variedContext(i));
    }
    log.info("Warm-up complete. Measuring {} evaluations...", MEASURE_COUNT);
  }

  @AfterAll
  static void tearDown() {
    if (spelExecutor != null) {
      spelExecutor.shutdownNow();
    }
  }

  @Test
  @DisplayName("p99 latency target — soft <10ms, hard <50ms")
  void latencyBenchmark() {
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
    double p99Millis = p99Micros / 1_000.0;

    log.info(
        "BENCHMARK p50={} µs  p95={} µs  p99={} µs  (= {} ms)  [n={}, rules=20]",
        String.format("%.2f", p50Micros),
        String.format("%.2f", p95Micros),
        String.format("%.2f", p99Micros),
        String.format("%.3f", p99Millis),
        MEASURE_COUNT);

    if (p99Millis < 10.0) {
      log.info("PASS: p99 {} ms < 10 ms target", String.format("%.3f", p99Millis));
    } else if (p99Millis < 50.0) {
      log.warn(
          "SOFT FAIL: p99 {} ms exceeds 10 ms target but within 50 ms hard limit "
              + "(document numbers in commit message)",
          String.format("%.3f", p99Millis));
    } else {
      log.error("HARD FAIL: p99 {} ms exceeds 50 ms hard limit", String.format("%.3f", p99Millis));
    }

    org.junit.jupiter.api.Assertions.assertTrue(
        p99Millis < 50.0,
        String.format("p99 latency %.3f ms exceeded 50 ms hard limit", p99Millis));
  }

  // ── Fixtures ────────────────────────────────────────────────────────────

  private static List<CompiledRule> seedRules() {
    SpelExpressionParser parser = new SpelExpressionParser();
    List<CompiledRule> rules = new ArrayList<>();

    rules.add(makeRule(parser, "High Amount >10k", "amount.amount > 10000", 30, RuleAction.FLAG));
    rules.add(
        makeRule(parser, "Very High Amount >50k", "amount.amount > 50000", 50, RuleAction.BLOCK));
    rules.add(makeRule(parser, "Tiny Amount <10", "amount.amount < 10", 5, RuleAction.FLAG));
    rules.add(makeRule(parser, "Round Amount", "amount.amount % 1000 == 0", 10, RuleAction.FLAG));

    rules.add(makeRule(parser, "BKASH vendor", "vendor == 'BKASH'", 5, RuleAction.FLAG));
    rules.add(makeRule(parser, "NAGAD vendor", "vendor == 'NAGAD'", 5, RuleAction.FLAG));
    rules.add(makeRule(parser, "Unknown vendor", "vendor == 'UNKNOWN'", 40, RuleAction.BLOCK));

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

  // ── Fake collaborators (only the non-CPU components are faked) ───────────

  static class FakeCompiledRuleCache extends CompiledRuleCache {
    private final List<CompiledRule> rules;

    FakeCompiledRuleCache(List<CompiledRule> rules) {
      super(null, null, null);
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
}
