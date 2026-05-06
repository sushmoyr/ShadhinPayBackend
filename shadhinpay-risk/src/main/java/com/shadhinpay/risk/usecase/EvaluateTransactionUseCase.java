package com.shadhinpay.risk.usecase;

/**
 * Synchronously evaluates a transaction against the active rule engine and returns a {@link
 * RiskDecision}.
 *
 * <p><b>Caller:</b> {@code payment-core} during the pre-flight phase of {@code
 * InitiatePaymentUseCase}, before any MFS dispatch and before any quota reservation is confirmed.
 *
 * <p><b>Guarantees:</b>
 *
 * <ul>
 *   <li>Pure read-then-evaluate: this call may persist an audit row but never mutates business
 *       state outside the risk module.
 *   <li>Total: a {@link RiskDecision} is always returned. Implementations must not throw on rule
 *       evaluation errors; they default to {@link RiskDecision.Action#FLAG} so the orchestrator can
 *       fail safe.
 *   <li>Bounded latency: implementations should target sub-50ms p99 (per {@code
 *       DOCS/features/risk/TECH_SPEC.md} §6).
 * </ul>
 */
public interface EvaluateTransactionUseCase {

  /**
   * Evaluates the supplied {@link TransactionContext} and returns a {@link RiskDecision}.
   *
   * @param ctx immutable transaction snapshot to evaluate
   * @return the resulting {@link RiskDecision}; never {@code null}
   */
  RiskDecision execute(TransactionContext ctx);
}
