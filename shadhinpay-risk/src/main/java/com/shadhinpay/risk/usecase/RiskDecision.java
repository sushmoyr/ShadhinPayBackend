package com.shadhinpay.risk.usecase;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Outcome of a synchronous risk evaluation.
 *
 * <p>Returned by {@link EvaluateTransactionUseCase}. {@code payment-core} maps {@link Action} to
 * orchestration behaviour: {@code ALLOW} continues, {@code FLAG} parks the transaction in {@code
 * PENDING_RISK}, {@code BLOCK} short-circuits with {@code 403 RISK_REJECTED}.
 *
 * @param action effective decision the orchestrator must honour
 * @param score aggregated rule score; comparable across transactions of the same merchant tier
 * @param triggeredRuleIds identifiers of every rule that matched (defensive copy held)
 * @param reason human-readable explanation suitable for risk audit logs (never returned to the end
 *     customer)
 */
@SuppressWarnings("PMD.UnusedAssignment")
public record RiskDecision(Action action, int score, List<UUID> triggeredRuleIds, String reason) {

  /** Final decision the orchestrator must apply. */
  public enum Action {
    /** Allow the transaction to proceed to MFS dispatch. */
    ALLOW,
    /** Hold the transaction for manual admin review; do not dispatch to MFS. */
    FLAG,
    /** Reject the transaction immediately with {@code 403}. */
    BLOCK
  }

  public RiskDecision {
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
    triggeredRuleIds = triggeredRuleIds == null ? List.of() : List.copyOf(triggeredRuleIds);
  }
}
