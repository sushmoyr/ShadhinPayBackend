package com.shadhinpay.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.shadhinpay.common.money.Money;
import com.shadhinpay.ledger.dto.TrialBalanceReportDto;
import com.shadhinpay.ledger.entity.JournalEntry;
import com.shadhinpay.ledger.entity.LedgerAccount;
import com.shadhinpay.ledger.entity.Posting;
import com.shadhinpay.ledger.entity.PostingType;
import com.shadhinpay.ledger.job.LedgerIntegrityJob;
import com.shadhinpay.ledger.repository.JournalEntryRepository;
import com.shadhinpay.ledger.repository.LedgerAccountRepository;
import com.shadhinpay.ledger.repository.PostingRepository;
import com.shadhinpay.ledger.usecase.JournalEntryRequest;
import com.shadhinpay.ledger.usecase.PostingRequest;
import com.shadhinpay.ledger.usecase.RecordJournalEntryUseCase;
import com.shadhinpay.ledger.usecase.internal.VerifyTrialBalanceUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

class LedgerIntegrityIT extends AbstractLedgerIntegrationTest {

  @TestConfiguration
  static class TestConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
      return NoOpPasswordEncoder.getInstance();
    }
  }

  @Autowired private RecordJournalEntryUseCase recordUseCase;
  @Autowired private VerifyTrialBalanceUseCase verifyTrialBalanceUseCase;
  @Autowired private LedgerIntegrityJob integrityJob;
  @Autowired private LedgerAccountRepository accountRepository;
  @Autowired private JournalEntryRepository journalEntryRepository;
  @Autowired private PostingRepository postingRepository;

  @Test
  void waveARegression_cleanLedgerProducesCleanReport() {
    UUID merchantId = UUID.randomUUID();
    LedgerAccount escrow = accountRepository.findByCodeAndCurrency("ESCROW", "BDT").get(0);
    LedgerAccount revenue =
        accountRepository.findByCodeAndCurrency("PLATFORM_REVENUE", "BDT").get(0);

    JournalEntryRequest req =
        new JournalEntryRequest(
            "PAYMENT",
            UUID.randomUUID().toString(),
            "Integrity regression",
            List.of(
                new PostingRequest(escrow.getId(), Money.of(100, "BDT"), PostingRequest.Type.DEBIT),
                new PostingRequest(merchantId, Money.of(-98, "BDT"), PostingRequest.Type.CREDIT),
                new PostingRequest(
                    revenue.getId(), Money.of(-2, "BDT"), PostingRequest.Type.CREDIT)),
            Instant.now());
    recordUseCase.execute(req);

    // Direct bean invocation simulates the scheduled job firing.
    integrityJob.runDailyIntegrityCheck();

    TrialBalanceReportDto report = verifyTrialBalanceUseCase.execute();
    assertThat(report.globalSumZero()).isTrue();
    assertThat(report.balanceMismatches()).isEmpty();
  }

  @Test
  @Transactional
  void integrityJob_surfacesMismatchWhenPostingIsInjectedDirectly() {
    LedgerAccount escrow = accountRepository.findByCodeAndCurrency("ESCROW", "BDT").get(0);

    // Bypass the use case to fabricate a balance-vs-postings divergence on a single account.
    JournalEntry orphan =
        journalEntryRepository.saveAndFlush(
            new JournalEntry(
                new JournalEntryRequest(
                    "ADJUSTMENT",
                    "integrity-injected-" + UUID.randomUUID(),
                    "Injected orphan",
                    List.of(
                        new PostingRequest(
                            escrow.getId(), Money.of(1, "BDT"), PostingRequest.Type.DEBIT)),
                    Instant.now())));
    Posting orphanPosting =
        postingRepository.saveAndFlush(
            new Posting(orphan, escrow, Money.of(50, "BDT"), PostingType.DEBIT));

    TrialBalanceReportDto report = verifyTrialBalanceUseCase.execute();

    assertThat(report.balanceMismatches())
        .as("Injected posting must surface as an account mismatch")
        .anyMatch(r -> r.accountId().equals(escrow.getId()) && r.accountCode().equals("ESCROW"));

    // Back out injection so the suite stays self-contained.
    postingRepository.delete(orphanPosting);
    journalEntryRepository.delete(orphan);
  }
}
