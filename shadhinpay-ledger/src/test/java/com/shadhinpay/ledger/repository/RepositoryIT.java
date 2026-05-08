package com.shadhinpay.ledger.repository;

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
import org.springframework.context.annotation.Import;
import com.shadhinpay.common.money.MoneyConverter;

@DataJpaTest(properties = {
    "spring.jpa.show-sql=true",
    "spring.jpa.properties.hibernate.format_sql=true"
})
class RepositoryIT {

  @Autowired private LedgerAccountRepository accountRepository;
  @Autowired private JournalEntryRepository journalRepository;
  @Autowired private PostingRepository postingRepository;

  @Test
  void testSqlTrace() {
    LedgerAccount escrow = new LedgerAccount(null, LedgerAccountType.CLEARING, "ESCROW", 0, "BDT");
    LedgerAccount merchant = new LedgerAccount(UUID.randomUUID(), LedgerAccountType.LIABILITY, "MERCHANT_PAYABLE", 0, "BDT");
    LedgerAccount revenue = new LedgerAccount(null, LedgerAccountType.REVENUE, "PLATFORM_REVENUE", 0, "BDT");

    accountRepository.saveAll(List.of(escrow, merchant, revenue));

    JournalEntryRequest req = new JournalEntryRequest(
        "PAYMENT", "txn-trace", "Trace", List.of(), Instant.now()
    );
    JournalEntry journal = new JournalEntry(req);
    journalRepository.save(journal);

    Posting p1 = new Posting(journal, escrow, Money.of(100, "BDT"), PostingType.DEBIT);
    Posting p2 = new Posting(journal, merchant, Money.of(99, "BDT"), PostingType.CREDIT);
    Posting p3 = new Posting(journal, revenue, Money.of(1, "BDT"), PostingType.CREDIT);

    postingRepository.saveAll(List.of(p1, p2, p3));
    
    assertThat(postingRepository.count()).isEqualTo(3);
  }
}
