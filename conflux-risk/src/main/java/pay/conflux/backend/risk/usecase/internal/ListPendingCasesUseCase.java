package pay.conflux.backend.risk.usecase.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pay.conflux.backend.risk.dto.RiskCaseDto;
import pay.conflux.backend.risk.usecase.RiskDecision;

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
