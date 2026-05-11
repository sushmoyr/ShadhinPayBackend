package pay.conflux.backend.risk.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pay.conflux.backend.risk.entity.RiskRule;
import pay.conflux.backend.risk.events.RiskRuleChangedEvent;
import pay.conflux.backend.risk.repository.RiskRuleRepository;

/**
 * Keeps the {@link CompiledRuleCache} in sync with {@code risk_rules} mutations. Runs after the
 * publishing transaction commits, so if the DB write rolls back the cache is not touched.
 *
 * <p>Fails OPEN: any listener exception is logged at ERROR and swallowed. Drift is bounded because
 * {@code CompiledRuleCache.loadAll()} re-hydrates on application restart. The evaluation pipeline
 * itself is fail-CLOSED — a stale or missing cache entry never produces a permissive decision.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompiledRuleCacheListener {

  private final CompiledRuleCache cache;
  private final SafeSpelEvaluator evaluator;
  private final RiskRuleRepository ruleRepository;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(RiskRuleChangedEvent event) {
    try {
      switch (event.kind()) {
        case CREATED, UPDATED -> {
          RiskRule rule = ruleRepository.findById(event.ruleId()).orElse(null);
          if (rule == null || !rule.isActive() || rule.isDeleted()) {
            cache.invalidate(event.ruleId());
            return;
          }
          Expression expr = evaluator.compile(rule);
          if (expr != null) {
            cache.put(rule, expr);
          } else {
            cache.invalidate(event.ruleId());
          }
        }
        case DISABLED -> cache.invalidate(event.ruleId());
        default -> log.warn("Unhandled RiskRuleChangedEvent kind {}", event.kind());
      }
    } catch (Exception e) {
      log.error(
          "CompiledRuleCache hot-update failed for rule {} ({}): {}",
          event.ruleId(),
          event.kind(),
          e.getMessage(),
          e);
    }
  }
}
