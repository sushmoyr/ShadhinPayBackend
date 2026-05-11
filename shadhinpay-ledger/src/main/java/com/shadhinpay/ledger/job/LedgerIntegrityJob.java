package com.shadhinpay.ledger.job;

import com.shadhinpay.ledger.dto.AccountIntegrityRecord;
import com.shadhinpay.ledger.dto.TrialBalanceReportDto;
import com.shadhinpay.ledger.usecase.internal.VerifyTrialBalanceUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerIntegrityJob {

  private static final String FAILURE_COUNTER = "shadhinpay.ledger.integrity.failures";

  private final VerifyTrialBalanceUseCase verifyTrialBalanceUseCase;
  private final MeterRegistry meterRegistry;

  @Scheduled(cron = "0 0 3 * * *")
  public void runDailyIntegrityCheck() {
    log.info("Starting daily ledger integrity check");
    TrialBalanceReportDto report = verifyTrialBalanceUseCase.execute();

    if (report.globalSumZero() && report.balanceMismatches().isEmpty()) {
      log.info("Daily ledger integrity check PASSED: global SUM = 0, no per-account mismatches.");
      return;
    }

    if (!report.globalSumZero()) {
      log.warn("Daily ledger integrity check FAILED: global SUM != 0");
      meterRegistry.counter(FAILURE_COUNTER, "type", "global_sum").increment();
    }

    for (AccountIntegrityRecord mismatch : report.balanceMismatches()) {
      log.warn(
          "Ledger integrity mismatch: accountId={} accountCode={} expectedBalance={}"
              + " actualBalance={} delta={}",
          mismatch.accountId(),
          mismatch.accountCode(),
          mismatch.expectedBalance(),
          mismatch.actualBalance(),
          mismatch.delta());
      meterRegistry
          .counter(
              FAILURE_COUNTER, "type", "balance_mismatch", "accountCode", mismatch.accountCode())
          .increment();
    }
  }
}
