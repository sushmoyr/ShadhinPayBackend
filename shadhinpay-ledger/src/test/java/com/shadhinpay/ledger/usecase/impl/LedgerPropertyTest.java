package com.shadhinpay.ledger.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.shadhinpay.common.error.InvalidOperationStateException;
import com.shadhinpay.common.money.Money;
import com.shadhinpay.ledger.entity.LedgerAccount;
import com.shadhinpay.ledger.entity.LedgerAccountType;
import com.shadhinpay.ledger.entity.Posting;
import com.shadhinpay.ledger.repository.JournalEntryRepository;
import com.shadhinpay.ledger.repository.LedgerAccountRepository;
import com.shadhinpay.ledger.repository.PostingRepository;
import com.shadhinpay.ledger.usecase.JournalEntryRequest;
import com.shadhinpay.ledger.usecase.PostingRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;

class LedgerPropertyTest {

  @Property(tries = 200)
  void zeroSumJournalPersistsSuccessfully(@ForAll("balancedAmounts") List<Money> amounts) {
    JournalEntryRepository journalRepo = mock(JournalEntryRepository.class);
    LedgerAccountRepository accountRepo = mock(LedgerAccountRepository.class);
    PostingRepository postingRepo = mock(PostingRepository.class);

    RecordJournalEntryUseCaseImpl useCase =
        new RecordJournalEntryUseCaseImpl(journalRepo, accountRepo, postingRepo);

    UUID escrowId = UUID.randomUUID();
    UUID revenueId = UUID.randomUUID();

    LedgerAccount escrow = new LedgerAccount(null, LedgerAccountType.CLEARING, "ESCROW", 0, "BDT");
    LedgerAccount revenue =
        new LedgerAccount(null, LedgerAccountType.REVENUE, "PLATFORM_REVENUE", 0, "BDT");

    lenient().when(accountRepo.findById(escrowId)).thenReturn(Optional.of(escrow));
    lenient().when(accountRepo.findById(revenueId)).thenReturn(Optional.of(revenue));
    lenient()
        .when(
            accountRepo.findByOwnerIdAndCodeAndShardIdAndCurrency(
                any(), any(), org.mockito.ArgumentMatchers.anyInt(), any()))
        .thenAnswer(
            invocation -> {
              String code = invocation.getArgument(1);
              int shard = invocation.getArgument(2);
              if ("ESCROW".equals(code))
                return Optional.of(
                    new LedgerAccount(null, LedgerAccountType.CLEARING, "ESCROW", shard, "BDT"));
              if ("PLATFORM_REVENUE".equals(code))
                return Optional.of(
                    new LedgerAccount(
                        null, LedgerAccountType.REVENUE, "PLATFORM_REVENUE", shard, "BDT"));
              return Optional.empty();
            });
    lenient().when(journalRepo.existsBySourceTypeAndSourceId(any(), any())).thenReturn(false);

    List<PostingRequest> postings =
        List.of(
            new PostingRequest(escrowId, amounts.get(0), PostingRequest.Type.DEBIT),
            new PostingRequest(revenueId, amounts.get(1), PostingRequest.Type.CREDIT));

    JournalEntryRequest req =
        new JournalEntryRequest(
            "ADJUSTMENT", UUID.randomUUID().toString(), "Prop Test", postings, Instant.now());

    useCase.execute(req);

    ArgumentCaptor<Posting> captor = ArgumentCaptor.forClass(Posting.class);
    verify(postingRepo, times(2)).save(captor.capture());

    Money sum = Money.zero("BDT");
    for (Posting p : captor.getAllValues()) {
      Money absAmount =
          p.getType() == com.shadhinpay.ledger.entity.PostingType.DEBIT
              ? p.getAmount()
              : p.getAmount().negate();
      sum = sum.add(absAmount);
    }
    assertThat(sum.isZero()).isTrue();
  }

  @Property(tries = 50)
  void nonZeroSumJournalFails(@ForAll("unbalancedAmounts") List<Money> amounts) {
    JournalEntryRepository journalRepo = mock(JournalEntryRepository.class);
    LedgerAccountRepository accountRepo = mock(LedgerAccountRepository.class);
    PostingRepository postingRepo = mock(PostingRepository.class);

    RecordJournalEntryUseCaseImpl useCase =
        new RecordJournalEntryUseCaseImpl(journalRepo, accountRepo, postingRepo);

    UUID escrowId = UUID.randomUUID();
    UUID revenueId = UUID.randomUUID();

    List<PostingRequest> postings =
        List.of(
            new PostingRequest(escrowId, amounts.get(0), PostingRequest.Type.DEBIT),
            new PostingRequest(revenueId, amounts.get(1), PostingRequest.Type.CREDIT));

    JournalEntryRequest req =
        new JournalEntryRequest(
            "ADJUSTMENT", UUID.randomUUID().toString(), "Prop Test Fail", postings, Instant.now());

    assertThatThrownBy(() -> useCase.execute(req))
        .isInstanceOf(InvalidOperationStateException.class);
  }

  @Property(tries = 200)
  void currencyMismatchFails(@ForAll("currencyMismatchAmounts") List<Money> amounts) {
    JournalEntryRepository journalRepo = mock(JournalEntryRepository.class);
    LedgerAccountRepository accountRepo = mock(LedgerAccountRepository.class);
    PostingRepository postingRepo = mock(PostingRepository.class);

    RecordJournalEntryUseCaseImpl useCase =
        new RecordJournalEntryUseCaseImpl(journalRepo, accountRepo, postingRepo);

    UUID escrowId = UUID.randomUUID();
    UUID revenueId = UUID.randomUUID();

    List<PostingRequest> postings =
        List.of(
            new PostingRequest(escrowId, amounts.get(0), PostingRequest.Type.DEBIT),
            new PostingRequest(revenueId, amounts.get(1), PostingRequest.Type.CREDIT));

    JournalEntryRequest req =
        new JournalEntryRequest(
            "ADJUSTMENT", UUID.randomUUID().toString(), "Mismatch Test", postings, Instant.now());

    assertThatThrownBy(() -> useCase.execute(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Currency mismatch");
  }

  @Property(tries = 200)
  void balanceEqualsPostingsSingleAccount(@ForAll("randomAmounts") List<Money> amounts) {
    JournalEntryRepository journalRepo = mock(JournalEntryRepository.class);
    LedgerAccountRepository accountRepo = mock(LedgerAccountRepository.class);
    PostingRepository postingRepo = mock(PostingRepository.class);

    RecordJournalEntryUseCaseImpl useCase =
        new RecordJournalEntryUseCaseImpl(journalRepo, accountRepo, postingRepo);

    UUID merchantId = UUID.randomUUID();
    UUID escrowId = UUID.randomUUID();

    LedgerAccount escrow = new LedgerAccount(null, LedgerAccountType.CLEARING, "ESCROW", 0, "BDT");
    LedgerAccount merchantAcc =
        new LedgerAccount(merchantId, LedgerAccountType.LIABILITY, "MERCHANT_PAYABLE", 0, "BDT");

    lenient().when(accountRepo.findById(escrowId)).thenReturn(Optional.of(escrow));
    lenient().when(accountRepo.findById(merchantId)).thenReturn(Optional.of(merchantAcc));
    lenient()
        .when(
            accountRepo.findByOwnerIdAndCodeAndShardIdAndCurrency(
                any(), any(), org.mockito.ArgumentMatchers.anyInt(), any()))
        .thenAnswer(
            invocation -> {
              String code = invocation.getArgument(1);
              int shard = invocation.getArgument(2);
              if ("ESCROW".equals(code))
                return Optional.of(
                    new LedgerAccount(null, LedgerAccountType.CLEARING, "ESCROW", shard, "BDT"));
              return Optional.empty();
            });
    lenient().when(journalRepo.existsBySourceTypeAndSourceId(any(), any())).thenReturn(false);

    List<PostingRequest> sequence = new ArrayList<>();

    for (int i = 0; i < amounts.size(); i++) {
      Money amt = amounts.get(i);
      PostingRequest.Type type =
          amt.isPositive() ? PostingRequest.Type.DEBIT : PostingRequest.Type.CREDIT;
      PostingRequest merchantPosting = new PostingRequest(merchantId, amt, type);
      sequence.add(merchantPosting);

      PostingRequest.Type offsetType =
          type == PostingRequest.Type.DEBIT
              ? PostingRequest.Type.CREDIT
              : PostingRequest.Type.DEBIT;
      PostingRequest offsetPosting = new PostingRequest(escrowId, amt.negate(), offsetType);

      JournalEntryRequest req =
          new JournalEntryRequest(
              "ADJUSTMENT",
              UUID.randomUUID().toString(),
              "Prop Test Seq " + i,
              List.of(merchantPosting, offsetPosting),
              Instant.now());
      useCase.execute(req);
    }

    Money expectedBalance = Money.zero("BDT");
    for (PostingRequest p : sequence) {
      Money absAmount = p.amount().isNegative() ? p.amount().negate() : p.amount();
      com.shadhinpay.ledger.entity.PostingType entityType =
          p.type() == PostingRequest.Type.DEBIT
              ? com.shadhinpay.ledger.entity.PostingType.DEBIT
              : com.shadhinpay.ledger.entity.PostingType.CREDIT;
      expectedBalance =
          LedgerAccountType.LIABILITY.applyDelta(expectedBalance, entityType, absAmount);
    }

    assertThat(merchantAcc.getBalance().amount()).isEqualByComparingTo(expectedBalance.amount());
  }

  @Provide
  Arbitrary<List<Money>> balancedAmounts() {
    return Arbitraries.integers()
        .between(1, 10000)
        .map(
            val -> {
              Money m1 = Money.of(val, "BDT");
              Money m2 = m1.negate();
              return List.of(m1, m2);
            });
  }

  @Provide
  Arbitrary<List<Money>> unbalancedAmounts() {
    return Arbitraries.integers()
        .between(1, 10000)
        .list()
        .ofSize(2)
        .map(
            ints -> {
              Money m1 = Money.of(ints.get(0), "BDT");
              Money m2 = Money.of(ints.get(1) + 1, "BDT");
              if (m1.amount().compareTo(m2.amount()) == 0) m2 = m2.add(Money.of(1, "BDT"));
              return List.of(m1, m2.negate());
            });
  }

  @Provide
  Arbitrary<List<Money>> currencyMismatchAmounts() {
    return Arbitraries.integers()
        .between(1, 10000)
        .map(
            val -> {
              Money m1 = Money.of(val, "BDT");
              Money m2 = Money.of(-val, "USD");
              return List.of(m1, m2);
            });
  }

  @Provide
  Arbitrary<List<Money>> randomAmounts() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .list()
        .ofMinSize(1)
        .ofMaxSize(10)
        .map(
            ints -> {
              List<Money> res = new ArrayList<>();
              for (Integer val : ints) {
                if (val == 0) val = 1;
                res.add(Money.of(val, "BDT"));
              }
              return res;
            });
  }
}
