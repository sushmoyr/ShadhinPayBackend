package com.shadhinpay.risk.engine;

import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.usecase.TransactionContext;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * Hardened SpEL evaluator. Uses {@link SimpleEvaluationContext} with read-only data binding so type
 * references, bean references, constructors, and method invocation surface beyond the root object's
 * bean properties are denied by default. Each evaluation runs on a bounded executor with a 50ms
 * timeout.
 *
 * <p><b>Compile failure contract:</b> {@link #compile(RiskRule)} returns {@code null} on parse
 * failure and logs ERROR. The caller (e.g. {@code CompiledRuleCache} or {@code
 * CompiledRuleCacheListener}) is responsible for downstream action such as disabling the offending
 * rule in the database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SafeSpelEvaluator {

  private final SpelExpressionParser parser = new SpelExpressionParser();
  private final ExecutorService spelExecutorService;

  public Expression compile(RiskRule rule) {
    try {
      return parser.parseExpression(rule.getExpression());
    } catch (ParseException e) {
      log.error("Failed to parse SpEL expression for rule {}: {}", rule.getId(), e.getMessage());
      return null;
    }
  }

  public Boolean evaluate(Expression compiled, TransactionContext ctx) {
    if (compiled == null || ctx == null) {
      return false;
    }

    Future<Boolean> future = spelExecutorService.submit(() -> doEvaluate(compiled, ctx));

    try {
      Boolean result = future.get(50, TimeUnit.MILLISECONDS);
      return result != null && result;
    } catch (TimeoutException e) {
      log.warn("Evaluation timed out for expression {}", compiled.getExpressionString());
      future.cancel(true);
      return false;
    } catch (Exception e) {
      log.warn(
          "Execution error for expression {}: {}", compiled.getExpressionString(), e.getMessage());
      future.cancel(true);
      return false;
    }
  }

  private Boolean doEvaluate(Expression compiled, TransactionContext ctx) {
    try {
      SimpleEvaluationContext context =
          SimpleEvaluationContext.forReadOnlyDataBinding().withRootObject(ctx).build();
      return compiled.getValue(context, Boolean.class);
    } catch (RuntimeException e) {
      log.warn(
          "Evaluation failed for expression {}: {}",
          compiled.getExpressionString(),
          e.getMessage());
      return false;
    }
  }
}
