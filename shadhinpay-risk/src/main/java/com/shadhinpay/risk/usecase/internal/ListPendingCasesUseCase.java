package com.shadhinpay.risk.usecase.internal;

import com.shadhinpay.risk.dto.RiskCaseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Lists {@code RiskEvaluation} rows where {@code decision == FLAG} and no admin action has been
 * recorded yet ({@code reviewDecision IS NULL}).
 *
 * <p>This is an internal administrative use case. It does not unblock the original transaction;
 * that integration belongs to Wave B {@code payment-core}.
 */
public interface ListPendingCasesUseCase {
  Page<RiskCaseDto> execute(Pageable pageable);
}
