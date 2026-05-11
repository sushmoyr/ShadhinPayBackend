package pay.conflux.backend.ledger.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.ledger.repository.JournalEntryRepository;
import pay.conflux.backend.ledger.repository.LedgerAccountRepository;
import pay.conflux.backend.ledger.repository.PostingRepository;
import pay.conflux.backend.ledger.usecase.JournalEntryRequest;
import pay.conflux.backend.ledger.usecase.PostingRequest;

@ExtendWith(MockitoExtension.class)
class RecordJournalEntryUseCaseImplTest {

  @Mock private JournalEntryRepository journalEntryRepository;
  @Mock private LedgerAccountRepository ledgerAccountRepository;
  @Mock private PostingRepository postingRepository;

  @InjectMocks private RecordJournalEntryUseCaseImpl useCase;

  @Test
  void execute_idempotent_returnsSilentlyOnDuplicateSource() {
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
  void execute_rejectsNonZeroSum() {
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

  @Test
  void execute_rejectsCurrencyMismatch() {
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
                    UUID.randomUUID(), Money.of(-10, "USD"), PostingRequest.Type.CREDIT)),
            Instant.now());

    assertThatThrownBy(() -> useCase.execute(req))
        .isInstanceOf(InvalidOperationStateException.class)
        .hasMessageContaining("Currency mismatch in journal postings");

    verify(journalEntryRepository, never()).save(any());
  }

  @Test
  void execute_rejectsPositiveAmountTaggedAsCredit() {
    when(journalEntryRepository.existsBySourceTypeAndSourceId("PAYMENT", "S1")).thenReturn(false);

    JournalEntryRequest req =
        new JournalEntryRequest(
            "PAYMENT",
            "S1",
            "Inconsistent sign",
            List.of(
                new PostingRequest(
                    UUID.randomUUID(), Money.of(100, "BDT"), PostingRequest.Type.DEBIT),
                new PostingRequest(
                    UUID.randomUUID(), Money.of(100, "BDT"), PostingRequest.Type.CREDIT)),
            Instant.now());

    assertThatThrownBy(() -> useCase.execute(req))
        .isInstanceOf(InvalidOperationStateException.class)
        .hasMessageContaining("inconsistent with type");

    verify(journalEntryRepository, never()).save(any());
  }

  @Test
  void execute_rejectsNegativeAmountTaggedAsDebit() {
    when(journalEntryRepository.existsBySourceTypeAndSourceId("PAYMENT", "S2")).thenReturn(false);

    JournalEntryRequest req =
        new JournalEntryRequest(
            "PAYMENT",
            "S2",
            "Inconsistent sign",
            List.of(
                new PostingRequest(
                    UUID.randomUUID(), Money.of(-100, "BDT"), PostingRequest.Type.DEBIT),
                new PostingRequest(
                    UUID.randomUUID(), Money.of(-100, "BDT"), PostingRequest.Type.CREDIT)),
            Instant.now());

    assertThatThrownBy(() -> useCase.execute(req))
        .isInstanceOf(InvalidOperationStateException.class)
        .hasMessageContaining("inconsistent with type");
  }

  @Test
  void ledgerShardSelector_distributesUniformlyAcrossTenShards() {
    int[] shardCounts = new int[10];
    int n = 10_000;

    for (int i = 0; i < n; i++) {
      int shardId = LedgerShardSelector.selectShard(UUID.randomUUID().toString());
      assertThat(shardId).isBetween(0, 9);
      shardCounts[shardId]++;
    }

    double expected = n / 10.0;
    double chiSquare = 0.0;
    for (int count : shardCounts) {
      chiSquare += Math.pow(count - expected, 2) / expected;
    }

    // 9 dof, 99% confidence — critical value 21.67
    assertThat(chiSquare).isLessThan(21.67);
  }

  @Test
  void ledgerShardSelector_neverReturnsNegativeEvenForNegativeHashCodes() {
    // Sample many strings whose hashCode is < 0 (Math.abs trap would map MIN_VALUE→negative).
    int sampled = 0;
    for (int i = 0; sampled < 100 && i < 100_000; i++) {
      String s = "shard-" + i;
      if (s.hashCode() < 0) {
        assertThat(LedgerShardSelector.selectShard(s)).isBetween(0, 9);
        sampled++;
      }
    }
    assertThat(sampled).as("collected at least 100 negative-hash inputs").isGreaterThan(0);
  }
}
