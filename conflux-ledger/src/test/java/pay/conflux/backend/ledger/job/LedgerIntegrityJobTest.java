package pay.conflux.backend.ledger.job;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.ledger.dto.AccountIntegrityRecord;
import pay.conflux.backend.ledger.dto.TrialBalanceReportDto;
import pay.conflux.backend.ledger.usecase.internal.VerifyTrialBalanceUseCase;

@ExtendWith(MockitoExtension.class)
class LedgerIntegrityJobTest {

  @Mock private VerifyTrialBalanceUseCase verifyTrialBalanceUseCase;
  @Mock private MeterRegistry meterRegistry;
  @Mock private Counter globalSumCounter;
  @Mock private Counter mismatchCounter;

  @InjectMocks private LedgerIntegrityJob job;

  @Test
  void shouldNotIncrementCounterOnCleanReport() {
    TrialBalanceReportDto cleanReport = new TrialBalanceReportDto(true, List.of(), Instant.now());

    when(verifyTrialBalanceUseCase.execute()).thenReturn(cleanReport);

    job.runDailyIntegrityCheck();

    verify(meterRegistry, never()).counter(anyString());
  }

  @Test
  void shouldIncrementCounterOnGlobalSumFailure() {
    TrialBalanceReportDto failedReport = new TrialBalanceReportDto(false, List.of(), Instant.now());

    when(verifyTrialBalanceUseCase.execute()).thenReturn(failedReport);
    when(meterRegistry.counter(
            eq("conflux.ledger.integrity.failures"), eq("type"), eq("global_sum")))
        .thenReturn(globalSumCounter);

    job.runDailyIntegrityCheck();

    verify(globalSumCounter).increment();
  }

  @Test
  void shouldIncrementCounterOnPerAccountMismatch() {
    AccountIntegrityRecord mismatch =
        new AccountIntegrityRecord(
            UUID.randomUUID(), "ESCROW", null, "100.0000", "90.0000", "-10.0000");
    TrialBalanceReportDto mismatchedReport =
        new TrialBalanceReportDto(false, List.of(mismatch), Instant.now());

    when(verifyTrialBalanceUseCase.execute()).thenReturn(mismatchedReport);
    when(meterRegistry.counter(
            eq("conflux.ledger.integrity.failures"), eq("type"), eq("global_sum")))
        .thenReturn(globalSumCounter);
    when(meterRegistry.counter(
            eq("conflux.ledger.integrity.failures"),
            eq("type"),
            eq("balance_mismatch"),
            eq("accountCode"),
            eq("ESCROW")))
        .thenReturn(mismatchCounter);

    job.runDailyIntegrityCheck();

    verify(globalSumCounter).increment();
    verify(mismatchCounter).increment();
  }
}
