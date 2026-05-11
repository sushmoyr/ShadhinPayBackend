package pay.conflux.backend.ledger;

import static org.assertj.core.api.Assertions.assertThat;

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
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.ledger.dto.TrialBalanceReportDto;
import pay.conflux.backend.ledger.entity.JournalEntry;
import pay.conflux.backend.ledger.entity.LedgerAccount;
import pay.conflux.backend.ledger.entity.Posting;
import pay.conflux.backend.ledger.entity.PostingType;
import pay.conflux.backend.ledger.job.LedgerIntegrityJob;
import pay.conflux.backend.ledger.repository.JournalEntryRepository;
import pay.conflux.backend.ledger.repository.LedgerAccountRepository;
import pay.conflux.backend.ledger.repository.PostingRepository;
import pay.conflux.backend.ledger.usecase.JournalEntryRequest;
import pay.conflux.backend.ledger.usecase.PostingRequest;
import pay.conflux.backend.ledger.usecase.RecordJournalEntryUseCase;
import pay.conflux.backend.ledger.usecase.internal.VerifyTrialBalanceUseCase;

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
