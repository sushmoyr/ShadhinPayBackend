package pay.conflux.backend.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.ledger.entity.LedgerAccount;
import pay.conflux.backend.ledger.entity.LedgerAccountType;
import pay.conflux.backend.ledger.repository.LedgerAccountRepository;
import pay.conflux.backend.ledger.usecase.GetAccountBalanceUseCase;
import pay.conflux.backend.ledger.usecase.JournalEntryRequest;
import pay.conflux.backend.ledger.usecase.PostingRequest;
import pay.conflux.backend.ledger.usecase.RecordJournalEntryUseCase;

class LedgerConcurrencyIT extends AbstractLedgerIntegrationTest {

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
  void hundredConcurrentJournalsAgainstSameMerchantAccount_settleWithRetries() throws Exception {
    int threadCount = 100;
    int poolSize = 20;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(poolSize);

    UUID merchantId = UUID.randomUUID();
    LedgerAccount merchantAcc =
        accountRepository.save(
            new LedgerAccount(
                merchantId, LedgerAccountType.LIABILITY, "MERCHANT_PAYABLE", 0, "BDT"));

    LedgerAccount escrowLogic = accountRepository.findByCodeAndCurrency("ESCROW", "BDT").get(0);
    LedgerAccount revenueLogic =
        accountRepository.findByCodeAndCurrency("PLATFORM_REVENUE", "BDT").get(0);

    AtomicInteger errorCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      int idx = i;
      executor.submit(
          () -> {
            try {
              startLatch.await();
              JournalEntryRequest req =
                  new JournalEntryRequest(
                      "PAYMENT",
                      UUID.randomUUID().toString(),
                      "Concurrent " + idx,
                      List.of(
                          new PostingRequest(
                              escrowLogic.getId(), Money.of(100, "BDT"), PostingRequest.Type.DEBIT),
                          new PostingRequest(
                              merchantAcc.getId(),
                              Money.of(-98, "BDT"),
                              PostingRequest.Type.CREDIT),
                          new PostingRequest(
                              revenueLogic.getId(),
                              Money.of(-2, "BDT"),
                              PostingRequest.Type.CREDIT)),
                      Instant.now());
              recordUseCase.execute(req);
            } catch (Exception e) {
              errorCount.incrementAndGet();
            } finally {
              endLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    boolean finished = endLatch.await(60, TimeUnit.SECONDS);
    executor.shutdown();
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

    assertThat(finished).as("all 100 tasks completed within 60s").isTrue();
    assertThat(errorCount.get()).as("retries absorbed every optimistic-lock collision").isZero();

    Money merchantBalance = getBalanceUseCase.execute(merchantId, "MERCHANT_PAYABLE");
    assertThat(merchantBalance.amount())
        .as("final balance equals 100 × 98 BDT, regardless of retry path")
        .isEqualByComparingTo("9800");
  }
}
