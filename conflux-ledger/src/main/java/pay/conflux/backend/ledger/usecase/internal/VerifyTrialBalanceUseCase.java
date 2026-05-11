package pay.conflux.backend.ledger.usecase.internal;

import pay.conflux.backend.ledger.dto.TrialBalanceReportDto;

/**
 * Runs the global trial-balance integrity check.
 *
 * <p><b>Internal to ledger.</b> Not exposed as a cross-module use-case contract.
 */
public interface VerifyTrialBalanceUseCase {

  TrialBalanceReportDto execute();
}
