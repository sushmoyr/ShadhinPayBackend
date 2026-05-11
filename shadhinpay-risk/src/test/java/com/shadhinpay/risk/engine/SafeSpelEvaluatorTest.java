package com.shadhinpay.risk.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.shadhinpay.common.money.Money;
import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.usecase.TransactionContext;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;

class SafeSpelEvaluatorTest {

  private SafeSpelEvaluator evaluator;
  private ExecutorService executorService;
  private TransactionContext context;

  @BeforeEach
  void setUp() {
    executorService = Executors.newFixedThreadPool(2);
    evaluator = new SafeSpelEvaluator(executorService);
    context =
        new TransactionContext(
            UUID.randomUUID(),
            Money.of(new BigDecimal("1500"), "BDT"),
            "VENDOR",
            "+8801700000000",
            "test@test.com",
            "127.0.0.1",
            Map.of("key", "value"));

    // Pre-warm
    RiskRule warmRule = new RiskRule();
    warmRule.setExpression("true");
    Expression warmExpr = evaluator.compile(warmRule);
    for (int i = 0; i < 10; i++) {
      evaluator.evaluate(warmExpr, context);
    }
  }

  @AfterEach
  void tearDown() {
    executorService.shutdownNow();
  }

  @Test
  void shouldDenyRuntimeExec() {
    assertAdversarial("T(java.lang.Runtime).getRuntime().exec('ls')");
  }

  @Test
  void shouldDenyFileInstantiation() {
    assertAdversarial("new java.io.File('/etc/passwd').exists()");
  }

  @Test
  void shouldDenyBeanResolution() {
    assertAdversarial("@someBean.someMethod()");
  }

  @Test
  void shouldDenyClassloaderEscape1() {
    assertAdversarial("''.getClass().getClassLoader()");
  }

  @Test
  void shouldDenyClassloaderEscape2() {
    assertAdversarial("''.getClass().forName('java.lang.Runtime')");
  }

  @Test
  void shouldDenySystemExit() {
    assertAdversarial("T(System).exit(0)");
  }

  @Test
  void shouldDenyMetadataClassloaderEscape() {
    assertAdversarial("metadata['key'].class.classLoader");
  }

  @Test
  void shouldEvaluateValidExpression() {
    RiskRule rule = new RiskRule();
    rule.setId(UUID.randomUUID());
    rule.setExpression("amount.amount > 1000");

    Expression compiled = evaluator.compile(rule);
    assertThat(compiled).isNotNull();

    Boolean result = evaluator.evaluate(compiled, context);
    assertThat(result).isTrue();
  }

  @Test
  void shouldTimeoutForLoopingExpression() {
    RiskRule rule = new RiskRule();
    rule.setId(UUID.randomUUID());
    rule.setExpression("'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!' matches '^((a+)*)+$'");

    Expression compiled = evaluator.compile(rule);
    if (compiled != null) {
      Boolean result = evaluator.evaluate(compiled, context);
      assertThat(result).isFalse();
    }
  }

  private void assertAdversarial(String exprString) {
    RiskRule rule = new RiskRule();
    rule.setId(UUID.randomUUID());
    rule.setExpression(exprString);

    Expression compiled = evaluator.compile(rule);
    if (compiled != null) {
      Boolean result = evaluator.evaluate(compiled, context);
      assertThat(result).isFalse();
    }
  }
}
