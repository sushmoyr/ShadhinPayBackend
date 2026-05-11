package com.shadhinpay.risk.usecase.internal;

import com.shadhinpay.risk.dto.RiskCaseDto;
import com.shadhinpay.risk.usecase.RiskDecision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Lists {@code RiskEvaluation} rows for admin case management. Filters by {@code decision} and
 * excludes already-reviewed cases ({@code reviewDecision IS NULL}).
 *
 * <p>This is an internal administrative use case. It does not unblock the original transaction;
 * that integration belongs to Wave B {@code payment-core}.
 */
public interface ListPendingCasesUseCase {
  Page<RiskCaseDto> execute(RiskDecision.Action status, Pageable pageable);
}
