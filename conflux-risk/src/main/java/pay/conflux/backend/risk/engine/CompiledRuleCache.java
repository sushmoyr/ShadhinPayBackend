package pay.conflux.backend.risk.engine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.stereotype.Component;
import pay.conflux.backend.risk.entity.RiskRule;
import pay.conflux.backend.risk.repository.RiskRuleRepository;
import pay.conflux.backend.risk.usecase.internal.DisableRiskRuleUseCase;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompiledRuleCache {

  private final RiskRuleRepository riskRuleRepository;
  private final SafeSpelEvaluator safeSpelEvaluator;
  private final DisableRiskRuleUseCase disableRiskRuleUseCase;

  private final Cache<UUID, CompiledRule> cache = Caffeine.newBuilder().build();

  @PostConstruct
  public void loadAll() {
    List<UUID> toDisable = new ArrayList<>();
    for (RiskRule rule : riskRuleRepository.findByActiveTrueAndDeletedFalse()) {
      Expression expr = safeSpelEvaluator.compile(rule);
      if (expr == null) {
        toDisable.add(rule.getId());
      } else {
        cache.put(rule.getId(), new CompiledRule(rule, expr));
      }
    }

    for (UUID id : toDisable) {
      try {
        disableRiskRuleUseCase.execute(id);
        log.warn("Disabled rule {} during startup because its SpEL expression failed to parse", id);
      } catch (Exception e) {
        log.error("Failed to disable bad-SpEL rule {} during startup: {}", id, e.getMessage());
      }
    }
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
