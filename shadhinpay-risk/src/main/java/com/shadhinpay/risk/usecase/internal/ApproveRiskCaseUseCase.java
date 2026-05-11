package com.shadhinpay.risk.usecase.internal;

import java.util.UUID;

/**
 * Records an admin's APPROVE decision against a flagged {@code RiskEvaluation}.
 *
 * <p>This is an internal administrative use case. It does not unblock the original transaction;
 * that integration belongs to Wave B {@code payment-core}. The admin review is recorded for audit
 * and downstream orchestration by {@code payment-core}.
 */
public interface ApproveRiskCaseUseCase {
  void execute(UUID evaluationId);
}
