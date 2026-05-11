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
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.ledger.entity.LedgerAccount;
import pay.conflux.backend.ledger.repository.LedgerAccountRepository;
import pay.conflux.backend.ledger.usecase.GetAccountBalanceUseCase;
import pay.conflux.backend.ledger.usecase.JournalEntryRequest;
import pay.conflux.backend.ledger.usecase.PostingRequest;
import pay.conflux.backend.ledger.usecase.RecordJournalEntryUseCase;

class LedgerEndToEndIT extends AbstractLedgerIntegrationTest {

  @TestConfiguration
  static class TestConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
      return NoOpPasswordEncoder.getInstance();
    }
  }

  @Autowired private RecordJournalEntryUseCase recordUseCase;
  @Autowired private GetAccountBalanceUseCase getBalanceUseCase;
  @Autowired private LedgerAccountRepository accountRepository;

  @Test
  void recordJournal_balancesAllThreeAccounts() {
    UUID merchantId = UUID.randomUUID();
    LedgerAccount escrowShard0 = accountRepository.findByCodeAndCurrency("ESCROW", "BDT").get(0);
    LedgerAccount revenueShard0 =
        accountRepository.findByCodeAndCurrency("PLATFORM_REVENUE", "BDT").get(0);

    JournalEntryRequest req =
        new JournalEntryRequest(
            "PAYMENT",
            UUID.randomUUID().toString(),
            "End-to-end test",
            List.of(
                new PostingRequest(
                    escrowShard0.getId(), Money.of(100, "BDT"), PostingRequest.Type.DEBIT),
                new PostingRequest(merchantId, Money.of(-99, "BDT"), PostingRequest.Type.CREDIT),
                new PostingRequest(
                    revenueShard0.getId(), Money.of(-1, "BDT"), PostingRequest.Type.CREDIT)),
            Instant.now());

    recordUseCase.execute(req);

    Money merchantBalance = getBalanceUseCase.execute(merchantId, "MERCHANT_PAYABLE");
    assertThat(merchantBalance.amount()).isEqualByComparingTo("99");

    Money escrowBalance = getBalanceUseCase.execute(null, "ESCROW");
    assertThat(escrowBalance.amount()).isEqualByComparingTo("100");

    Money revenueBalance = getBalanceUseCase.execute(null, "PLATFORM_REVENUE");
    assertThat(revenueBalance.amount()).isEqualByComparingTo("1");
  }

  @Test
  void shardedSystemBalance_aggregatesAcrossShards() {
    for (int i = 0; i < 100; i++) {
      String txnId = UUID.randomUUID().toString();
      LedgerAccount escrowLogic = accountRepository.findByCodeAndCurrency("ESCROW", "BDT").get(0);
      LedgerAccount revenueLogic =
          accountRepository.findByCodeAndCurrency("PLATFORM_REVENUE", "BDT").get(0);

      JournalEntryRequest req =
          new JournalEntryRequest(
              "FEE",
              txnId,
              "Fee " + i,
              List.of(
                  new PostingRequest(
                      escrowLogic.getId(), Money.of(10, "BDT"), PostingRequest.Type.DEBIT),
                  new PostingRequest(
                      revenueLogic.getId(), Money.of(-10, "BDT"), PostingRequest.Type.CREDIT)),
              Instant.now());
      recordUseCase.execute(req);
    }

    Money escrowTotal = getBalanceUseCase.execute(null, "ESCROW");
    assertThat(escrowTotal.amount()).isEqualByComparingTo("1000");

    long activeShards =
        accountRepository.findByCodeAndCurrency("ESCROW", "BDT").stream()
            .filter(a -> a.getBalance().isPositive())
            .count();

    assertThat(activeShards).isGreaterThanOrEqualTo(5);
  }
}
