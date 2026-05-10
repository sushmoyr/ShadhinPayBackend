package com.shadhinpay.ledger.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.error.InvalidOperationStateException;
import com.shadhinpay.common.money.Money;
import com.shadhinpay.ledger.entity.JournalEntry;
import com.shadhinpay.ledger.entity.LedgerAccount;
import com.shadhinpay.ledger.entity.LedgerAccountType;
import com.shadhinpay.ledger.entity.Posting;
import com.shadhinpay.ledger.entity.PostingType;
import com.shadhinpay.ledger.repository.JournalEntryRepository;
import com.shadhinpay.ledger.repository.LedgerAccountRepository;
import com.shadhinpay.ledger.repository.PostingRepository;
import com.shadhinpay.ledger.usecase.JournalEntryRequest;
import com.shadhinpay.ledger.usecase.PostingRequest;
import com.shadhinpay.ledger.usecase.RecordJournalEntryUseCase;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class RecordJournalEntryUseCaseImpl implements RecordJournalEntryUseCase {

  private final JournalEntryRepository journalEntryRepository;
  private final LedgerAccountRepository ledgerAccountRepository;
  private final PostingRepository postingRepository;

  private static final List<String> SYSTEM_ACCOUNT_CODES =
      List.of("ESCROW", "PLATFORM_REVENUE", "VENDOR_PAYABLE");
  private static final String DEFAULT_CURRENCY = "BDT";

  @Override
  @Retryable(
      retryFor = ObjectOptimisticLockingFailureException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 100, multiplier = 2, random = true))
  @Transactional
  public void execute(JournalEntryRequest request) {
    if (journalEntryRepository.existsBySourceTypeAndSourceId(
        request.sourceType(), request.sourceId())) {
      return; // Idempotent success
    }

    validateZeroSum(request);

    JournalEntry journal = new JournalEntry(request);
    journalEntryRepository.save(journal);

    for (PostingRequest p : request.postings()) {
      LedgerAccount account = resolveAccount(p.accountId(), request.sourceId());

      // Convert request sign to absolute amount + DB sign convention
      Money absAmount = p.amount().isNegative() ? p.amount().negate() : p.amount();
      PostingType type =
          p.type() == PostingRequest.Type.DEBIT ? PostingType.DEBIT : PostingType.CREDIT;

      account.applyPosting(absAmount, type);
      ledgerAccountRepository.save(account);

      Posting posting = new Posting(journal, account, absAmount, type);
      postingRepository.save(posting);
    }
  }

  @Recover
  public void recoverFromOptimisticLocking(
      ObjectOptimisticLockingFailureException e, JournalEntryRequest request) {
    throw new InvalidOperationStateException("Concurrent ledger update — please retry");
  }

  private void validateZeroSum(JournalEntryRequest request) {
    Money sum = Money.zero(DEFAULT_CURRENCY);
    try {
      for (PostingRequest p : request.postings()) {
        // The amount is already signed (+ for debit, - for credit)
        sum = sum.add(p.amount());
      }
    } catch (IllegalArgumentException e) {
      throw new InvalidOperationStateException(
          "Currency mismatch in journal postings: " + e.getMessage());
    }

    if (!sum.isZero()) {
      throw new InvalidOperationStateException(
          String.format(
              "Journal postings do not sum to zero for source %s:%s. Residual: %s",
              request.sourceType(), request.sourceId(), sum.amount()));
    }
  }

  private LedgerAccount resolveAccount(UUID requestedAccountId, String sourceId) {
    Optional<LedgerAccount> optAccount = ledgerAccountRepository.findById(requestedAccountId);

    if (optAccount.isPresent()) {
      LedgerAccount acc = optAccount.get();
      if (SYSTEM_ACCOUNT_CODES.contains(acc.getCode())) {
        int targetShardId = Math.abs(sourceId.hashCode()) % 10;
        if (acc.getShardId() != targetShardId) {
          return ledgerAccountRepository
              .findByOwnerIdAndCodeAndShardIdAndCurrency(
                  null, acc.getCode(), targetShardId, acc.getCurrency())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "System account shard missing: " + acc.getCode() + "_" + targetShardId));
        }
      }
      return acc;
    }

    // If not found, assume requestedAccountId is the ownerId for a per-merchant account
    return provisionMerchantAccount(
        requestedAccountId, "MERCHANT_PAYABLE", LedgerAccountType.LIABILITY);
  }

  private LedgerAccount provisionMerchantAccount(
      UUID ownerId, String code, LedgerAccountType type) {
    try {
      LedgerAccount newAccount = new LedgerAccount(ownerId, type, code, 0, DEFAULT_CURRENCY);
      return ledgerAccountRepository.saveAndFlush(newAccount);
    } catch (DataIntegrityViolationException e) {
      // Unique constraint violation -> reload
      return ledgerAccountRepository
          .findByOwnerIdAndCodeAndShardIdAndCurrency(ownerId, code, 0, DEFAULT_CURRENCY)
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Failed to load provisioned account for owner " + ownerId));
    }
  }
}
