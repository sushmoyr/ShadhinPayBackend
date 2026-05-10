package com.shadhinpay.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.shadhinpay.common.money.Money;
import com.shadhinpay.ledger.entity.LedgerAccount;
import com.shadhinpay.ledger.repository.LedgerAccountRepository;
import com.shadhinpay.ledger.usecase.GetAccountBalanceUseCase;
import com.shadhinpay.ledger.usecase.JournalEntryRequest;
import com.shadhinpay.ledger.usecase.PostingRequest;
import com.shadhinpay.ledger.usecase.RecordJournalEntryUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ledger_e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.defer-datasource-initialization=true",
    "spring.modulith.events.jdbc.schema-initialization.enabled=true",
    "spring.sql.init.mode=always",
    "spring.sql.init.data-locations=classpath:ledger-test-seed.sql",
    "shadhinpay.auth.token-secret=test-secret-test-secret-test-secret-test-secret",
    "shadhinpay.auth.token-expiration-ms=3600000",
    "REDIS_HOST=localhost",
    "REDIS_PASSWORD="
})
@ActiveProfiles("test")
public class LedgerEndToEndIT {

  @org.springframework.boot.test.context.TestConfiguration
  static class TestConfig {
    @org.springframework.context.annotation.Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
      return org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance();
    }
  }

  @Autowired private RecordJournalEntryUseCase recordUseCase;
  @Autowired private GetAccountBalanceUseCase getBalanceUseCase;
  @Autowired private LedgerAccountRepository accountRepository;

  @Test
  void testEndToEndRecord() {
    UUID merchantId = UUID.randomUUID();
    LedgerAccount escrowShard0 = accountRepository.findByCodeAndCurrency("ESCROW", "BDT").get(0);
    LedgerAccount revenueShard0 =
        accountRepository.findByCodeAndCurrency("PLATFORM_REVENUE", "BDT").get(0);

    JournalEntryRequest req =
        new JournalEntryRequest(
            "PAYMENT",
            UUID.randomUUID().toString(),
            "Test Txn",
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
    assertThat(escrowBalance.isPositive()).isTrue();

    Money revenueBalance = getBalanceUseCase.execute(null, "PLATFORM_REVENUE");
    assertThat(revenueBalance.isPositive()).isTrue();
  }

  @Test
  void testShardedBalance() {
    for (int i = 0; i < 100; i++) {
      String txnId = UUID.randomUUID().toString();
      LedgerAccount escrowLogic = accountRepository.findByCodeAndCurrency("ESCROW", "BDT").get(0);
      LedgerAccount revenueLogic =
          accountRepository.findByCodeAndCurrency("PLATFORM_REVENUE", "BDT").get(0);

      JournalEntryRequest req =
          new JournalEntryRequest(
              "FEE",
              txnId,
              "Fee",
              List.of(
                  new PostingRequest(
                      escrowLogic.getId(), Money.of(10, "BDT"), PostingRequest.Type.DEBIT),
                  new PostingRequest(
                      revenueLogic.getId(), Money.of(-10, "BDT"), PostingRequest.Type.CREDIT)),
              Instant.now());
      recordUseCase.execute(req);
    }

    Money escrowTotal = getBalanceUseCase.execute(null, "ESCROW");
    assertThat(escrowTotal.isPositive()).isTrue();

    long activeShards =
        accountRepository.findByCodeAndCurrency("ESCROW", "BDT").stream()
            .filter(a -> a.getBalance().isPositive())
            .count();

    assertThat(activeShards).isGreaterThanOrEqualTo(5);
  }
}
