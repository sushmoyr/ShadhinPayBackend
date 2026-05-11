package pay.conflux.backend.risk.events;

import java.util.UUID;

/**
 * Domain event signalling that a {@code RiskRule} has been created, updated, or disabled. Consumed
 * post-commit by {@code CompiledRuleCacheListener} to keep the Caffeine cache in sync.
 *
 * <p>Carries enough payload that the listener does not need to re-query the database (the entity
 * may have changed between commit and listener execution).
 */
public record RiskRuleChangedEvent(UUID ruleId, ChangeKind kind) {

  public enum ChangeKind {
    CREATED,
    UPDATED,
    DISABLED
  }
}
