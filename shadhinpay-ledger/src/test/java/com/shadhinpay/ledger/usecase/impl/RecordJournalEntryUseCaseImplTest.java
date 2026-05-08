package com.shadhinpay.ledger.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shadhinpay.common.error.InvalidOperationStateException;
import com.shadhinpay.common.money.Money;
import com.shadhinpay.ledger.repository.JournalEntryRepository;
import com.shadhinpay.ledger.repository.LedgerAccountRepository;
import com.shadhinpay.ledger.repository.PostingRepository;
import com.shadhinpay.ledger.usecase.JournalEntryRequest;
import com.shadhinpay.ledger.usecase.PostingRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordJournalEntryUseCaseImplTest {

  @Mock private JournalEntryRepository journalEntryRepository;
  @Mock private LedgerAccountRepository ledgerAccountRepository;
  @Mock private PostingRepository postingRepository;

  @InjectMocks private RecordJournalEntryUseCaseImpl useCase;

  @Test
  void testIdempotency() {
    when(journalEntryRepository.existsBySourceTypeAndSourceId("PAYMENT", "123")).thenReturn(true);

    JournalEntryRequest req =
        new JournalEntryRequest(
            "PAYMENT",
            "123",
            "Test",
            List.of(
                new PostingRequest(
                    UUID.randomUUID(), Money.of(10, "BDT"), PostingRequest.Type.DEBIT),
                new PostingRequest(
                    UUID.randomUUID(), Money.of(-10, "BDT"), PostingRequest.Type.CREDIT)),
            Instant.now());

    useCase.execute(req);

    verify(journalEntryRepository, never()).save(any());
  }

  @Test
  void testValidationThrowsOnNonZeroSum() {
    when(journalEntryRepository.existsBySourceTypeAndSourceId("PAYMENT", "123")).thenReturn(false);

    JournalEntryRequest req =
        new JournalEntryRequest(
            "PAYMENT",
            "123",
            "Test",
            List.of(
                new PostingRequest(
                    UUID.randomUUID(), Money.of(10, "BDT"), PostingRequest.Type.DEBIT),
                new PostingRequest(
                    UUID.randomUUID(), Money.of(-5, "BDT"), PostingRequest.Type.CREDIT)),
            Instant.now());

    assertThatThrownBy(() -> useCase.execute(req))
        .isInstanceOf(InvalidOperationStateException.class)
        .hasMessageContaining("Journal postings do not sum to zero");

    verify(journalEntryRepository, never()).save(any());
  }

  @Property(tries = 1)
  void shardSelectorChiSquareTest() {
    int[] shardCounts = new int[10];
    int n = 10000;

    for (int i = 0; i < n; i++) {
      String sourceId = UUID.randomUUID().toString();
      int shardId = Math.abs(sourceId.hashCode()) % 10;
      shardCounts[shardId]++;
    }

    double expected = n / 10.0;
    double chiSquare = 0.0;
    for (int count : shardCounts) {
      chiSquare += Math.pow(count - expected, 2) / expected;
    }

    // For 9 degrees of freedom, critical value at 99% confidence is 21.67
    assertThat(chiSquare).isLessThan(21.67);
  }
}
