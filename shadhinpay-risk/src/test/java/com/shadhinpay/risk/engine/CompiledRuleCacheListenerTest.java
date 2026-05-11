package com.shadhinpay.risk.engine;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.events.RiskRuleChangedEvent;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.expression.Expression;

@ExtendWith(MockitoExtension.class)
class CompiledRuleCacheListenerTest {

  @Mock private CompiledRuleCache cache;
  @Mock private SafeSpelEvaluator evaluator;
  @Mock private RiskRuleRepository ruleRepository;
  @Mock private Expression expression;

  private CompiledRuleCacheListener listener;

  @BeforeEach
  void setUp() {
    listener = new CompiledRuleCacheListener(cache, evaluator, ruleRepository);
  }

  @Test
  void created_putsCompiledRuleInCache() {
    UUID id = UUID.randomUUID();
    RiskRule rule = activeRule(id);
    when(ruleRepository.findById(id)).thenReturn(Optional.of(rule));
    when(evaluator.compile(rule)).thenReturn(expression);

    listener.on(new RiskRuleChangedEvent(id, RiskRuleChangedEvent.ChangeKind.CREATED));

    verify(cache).put(rule, expression);
    verify(cache, never()).invalidate(any());
  }

  @Test
  void updated_putsRecompiledRule() {
    UUID id = UUID.randomUUID();
    RiskRule rule = activeRule(id);
    when(ruleRepository.findById(id)).thenReturn(Optional.of(rule));
    when(evaluator.compile(rule)).thenReturn(expression);

    listener.on(new RiskRuleChangedEvent(id, RiskRuleChangedEvent.ChangeKind.UPDATED));

    verify(cache).put(rule, expression);
  }

  @Test
  void updated_compileReturnsNull_invalidates() {
    UUID id = UUID.randomUUID();
    RiskRule rule = activeRule(id);
    when(ruleRepository.findById(id)).thenReturn(Optional.of(rule));
    when(evaluator.compile(rule)).thenReturn(null);

    listener.on(new RiskRuleChangedEvent(id, RiskRuleChangedEvent.ChangeKind.UPDATED));

    verify(cache).invalidate(id);
    verify(cache, never()).put(any(), any());
  }

  @Test
  void created_ruleMissingInDb_invalidates() {
    UUID id = UUID.randomUUID();
    when(ruleRepository.findById(id)).thenReturn(Optional.empty());

    listener.on(new RiskRuleChangedEvent(id, RiskRuleChangedEvent.ChangeKind.CREATED));

    verify(cache).invalidate(id);
    verify(evaluator, never()).compile(any());
  }

  @Test
  void created_ruleInactive_invalidates() {
    UUID id = UUID.randomUUID();
    RiskRule rule = activeRule(id);
    rule.setActive(false);
    when(ruleRepository.findById(id)).thenReturn(Optional.of(rule));

    listener.on(new RiskRuleChangedEvent(id, RiskRuleChangedEvent.ChangeKind.CREATED));

    verify(cache).invalidate(id);
    verify(evaluator, never()).compile(any());
  }

  @Test
  void created_ruleSoftDeleted_invalidates() {
    UUID id = UUID.randomUUID();
    RiskRule rule = activeRule(id);
    rule.setDeleted(true);
    when(ruleRepository.findById(id)).thenReturn(Optional.of(rule));

    listener.on(new RiskRuleChangedEvent(id, RiskRuleChangedEvent.ChangeKind.CREATED));

    verify(cache).invalidate(id);
  }

  @Test
  void disabled_invalidates() {
    UUID id = UUID.randomUUID();

    listener.on(new RiskRuleChangedEvent(id, RiskRuleChangedEvent.ChangeKind.DISABLED));

    verify(cache).invalidate(id);
    verify(ruleRepository, never()).findById(any());
    verify(evaluator, never()).compile(any());
  }

  @Test
  void exceptionInCache_swallowed_failsOpen() {
    UUID id = UUID.randomUUID();
    doThrow(new RuntimeException("redis down")).when(cache).invalidate(id);

    // must not throw to the caller
    listener.on(new RiskRuleChangedEvent(id, RiskRuleChangedEvent.ChangeKind.DISABLED));
  }

  private static RiskRule activeRule(UUID id) {
    RiskRule rule = new RiskRule();
    rule.setId(id);
    rule.setExpression("true");
    rule.setActive(true);
    rule.setDeleted(false);
    return rule;
  }
}
