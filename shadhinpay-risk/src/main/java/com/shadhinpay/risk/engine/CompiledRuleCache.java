package com.shadhinpay.risk.engine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompiledRuleCache {

  private final RiskRuleRepository riskRuleRepository;
  private final SafeSpelEvaluator safeSpelEvaluator;

  private final Cache<UUID, CompiledRule> cache = Caffeine.newBuilder().build();

  @PostConstruct
  public void loadAll() {
    riskRuleRepository
        .findByActiveTrueAndDeletedFalse()
        .forEach(
            rule -> {
              Expression expr = safeSpelEvaluator.compile(rule);
              if (expr != null) {
                cache.put(rule.getId(), new CompiledRule(rule, expr));
              }
            });
  }

  public Collection<CompiledRule> snapshot() {
    return cache.asMap().values();
  }

  public void invalidate(UUID ruleId) {
    cache.invalidate(ruleId);
  }

  public void put(RiskRule rule, Expression expression) {
    cache.put(rule.getId(), new CompiledRule(rule, expression));
  }
}
