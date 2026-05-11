package com.shadhinpay.ledger.usecase.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shadhinpay.common.error.InvalidOperationStateException;
import com.shadhinpay.ledger.repository.JournalEntryRepository;
import com.shadhinpay.ledger.repository.LedgerAccountRepository;
import com.shadhinpay.ledger.repository.PostingRepository;
import com.shadhinpay.ledger.usecase.JournalEntryRequest;
import com.shadhinpay.ledger.usecase.RecordJournalEntryUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootTest(classes = RecordJournalEntryUseCaseImplRetryTest.TestConfig.class)
class RecordJournalEntryUseCaseImplRetryTest {

  @Configuration
  @EnableRetry
  static class TestConfig {
    @Bean
    public RecordJournalEntryUseCase recordJournalEntryUseCase(
        JournalEntryRepository journalEntryRepository,
        LedgerAccountRepository ledgerAccountRepository,
        PostingRepository postingRepository) {
      return new RecordJournalEntryUseCaseImpl(
          journalEntryRepository, ledgerAccountRepository, postingRepository);
    }
  }

  @Autowired private RecordJournalEntryUseCase useCase;

  @MockBean private JournalEntryRepository journalEntryRepository;
  @MockBean private LedgerAccountRepository ledgerAccountRepository;
  @MockBean private PostingRepository postingRepository;

  @Test
  void shouldRetryOnOptimisticLockingFailureAndSucceed() {
    JournalEntryRequest request =
        new JournalEntryRequest(
            "TEST",
            "1",
            "Desc",
            List.of(
                new com.shadhinpay.ledger.usecase.PostingRequest(
                    UUID.randomUUID(),
                    com.shadhinpay.common.money.Money.of(100, "BDT"),
                    com.shadhinpay.ledger.usecase.PostingRequest.Type.DEBIT),
                new com.shadhinpay.ledger.usecase.PostingRequest(
                    UUID.randomUUID(),
                    com.shadhinpay.common.money.Money.of(-100, "BDT"),
                    com.shadhinpay.ledger.usecase.PostingRequest.Type.CREDIT)),
            Instant.now());

    when(journalEntryRepository.existsBySourceTypeAndSourceId("TEST", "1"))
        .thenThrow(new ObjectOptimisticLockingFailureException("test", new RuntimeException()))
        .thenThrow(new ObjectOptimisticLockingFailureException("test", new RuntimeException()))
        .thenReturn(true); // Succeed on third try (idempotent return)

    useCase.execute(request);

    verify(journalEntryRepository, times(3)).existsBySourceTypeAndSourceId("TEST", "1");
  }

  @Test
  void shouldRecoverAfterMaxRetries() {
    JournalEntryRequest request =
        new JournalEntryRequest(
            "TEST",
            "2",
            "Desc",
            List.of(
                new com.shadhinpay.ledger.usecase.PostingRequest(
                    UUID.randomUUID(),
                    com.shadhinpay.common.money.Money.of(100, "BDT"),
                    com.shadhinpay.ledger.usecase.PostingRequest.Type.DEBIT),
                new com.shadhinpay.ledger.usecase.PostingRequest(
                    UUID.randomUUID(),
                    com.shadhinpay.common.money.Money.of(-100, "BDT"),
                    com.shadhinpay.ledger.usecase.PostingRequest.Type.CREDIT)),
            Instant.now());

    when(journalEntryRepository.existsBySourceTypeAndSourceId("TEST", "2"))
        .thenThrow(new ObjectOptimisticLockingFailureException("test", new RuntimeException()));

    assertThatThrownBy(() -> useCase.execute(request))
        .isInstanceOf(InvalidOperationStateException.class)
        .hasMessage("Concurrent ledger update — please retry");

    verify(journalEntryRepository, times(3)).existsBySourceTypeAndSourceId("TEST", "2");
  }
}
