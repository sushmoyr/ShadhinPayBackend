package com.shadhinpay.ledger.spec;

import static org.assertj.core.api.Assertions.assertThat;

import com.shadhinpay.common.money.Money;
import com.shadhinpay.ledger.entity.JournalEntry;
import com.shadhinpay.ledger.entity.LedgerAccount;
import com.shadhinpay.ledger.entity.LedgerAccountType;
import com.shadhinpay.ledger.entity.Posting;
import com.shadhinpay.ledger.entity.PostingType;
import com.shadhinpay.ledger.usecase.JournalEntryRequest;
import com.shadhinpay.ledger.usecase.PostingRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;

@DataJpaTest(properties = {"spring.jpa.show-sql=false"})
class JournalEntrySpecTest {

  @Autowired private com.shadhinpay.ledger.repository.JournalEntryRepository journalRepo;

  @Autowired private com.shadhinpay.ledger.repository.LedgerAccountRepository accountRepo;

  @Autowired private com.shadhinpay.ledger.repository.PostingRepository postingRepo;

  private static final UUID DUMMY_ACCOUNT = UUID.randomUUID();

  @Test
  void shouldFilterBySourceType() {
    JournalEntry j1 = persistJournal("PAYMENT", "1", Instant.now());
    JournalEntry j2 = persistJournal("REFUND", "2", Instant.now());

    Specification<JournalEntry> spec = JournalEntrySpec.filterBy("PAYMENT", null, null, null);
    List<JournalEntry> results = journalRepo.findAll(spec);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getSourceType()).isEqualTo("PAYMENT");
  }

  @Test
  void shouldFilterByDateRange() {
    Instant now = Instant.now();
    Instant earlier = now.minusSeconds(3600);
    Instant later = now.plusSeconds(3600);

    JournalEntry j1 = persistJournal("PAYMENT", "1", earlier);
    persistJournal("REFUND", "2", later);
    JournalEntry j3 = persistJournal("SETTLEMENT", "3", now);

    Specification<JournalEntry> spec =
        JournalEntrySpec.filterBy(null, earlier.minusSeconds(1), now.plusSeconds(1), null);
    List<JournalEntry> results = journalRepo.findAll(spec);

    assertThat(results).hasSize(2); // j1 and j3
  }

  @Test
  void shouldFilterByOwnerId() {
    UUID ownerId = UUID.randomUUID();

    LedgerAccount account =
        new LedgerAccount(ownerId, LedgerAccountType.LIABILITY, "MERCHANT_PAYABLE", 0, "BDT");
    accountRepo.saveAndFlush(account);

    JournalEntry journal =
        new JournalEntry(
            new JournalEntryRequest(
                "PAYMENT",
                "src-1",
                "desc",
                List.of(
                    new PostingRequest(
                        DUMMY_ACCOUNT, Money.of(100, "BDT"), PostingRequest.Type.DEBIT),
                    new PostingRequest(
                        DUMMY_ACCOUNT, Money.of(-100, "BDT"), PostingRequest.Type.CREDIT)),
                Instant.now()));
    journalRepo.saveAndFlush(journal);

    Posting posting = new Posting(journal, account, Money.of(100, "BDT"), PostingType.DEBIT);
    postingRepo.saveAndFlush(posting);

    // Create another journal with different owner
    UUID otherOwnerId = UUID.randomUUID();
    LedgerAccount otherAccount =
        new LedgerAccount(otherOwnerId, LedgerAccountType.LIABILITY, "MERCHANT_PAYABLE", 0, "BDT");
    accountRepo.saveAndFlush(otherAccount);
    JournalEntry otherJournal =
        new JournalEntry(
            new JournalEntryRequest(
                "REFUND",
                "src-2",
                "desc2",
                List.of(
                    new PostingRequest(
                        DUMMY_ACCOUNT, Money.of(50, "BDT"), PostingRequest.Type.DEBIT),
                    new PostingRequest(
                        DUMMY_ACCOUNT, Money.of(-50, "BDT"), PostingRequest.Type.CREDIT)),
                Instant.now()));
    journalRepo.saveAndFlush(otherJournal);
    Posting otherPosting =
        new Posting(otherJournal, otherAccount, Money.of(50, "BDT"), PostingType.CREDIT);
    postingRepo.saveAndFlush(otherPosting);

    Specification<JournalEntry> spec = JournalEntrySpec.filterBy(null, null, null, ownerId);
    List<JournalEntry> results = journalRepo.findAll(spec);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getSourceType()).isEqualTo("PAYMENT");
  }

  private JournalEntry persistJournal(String sourceType, String sourceId, Instant occurredAt) {
    JournalEntry entry =
        new JournalEntry(
            new JournalEntryRequest(
                sourceType,
                sourceId,
                "test",
                List.of(
                    new PostingRequest(
                        DUMMY_ACCOUNT, Money.of(10, "BDT"), PostingRequest.Type.DEBIT),
                    new PostingRequest(
                        DUMMY_ACCOUNT, Money.of(-10, "BDT"), PostingRequest.Type.CREDIT)),
                occurredAt));
    return journalRepo.saveAndFlush(entry);
  }
}
