package com.shadhinpay.ledger.usecase;

import com.shadhinpay.common.money.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * Single debit or credit posting against a ledger account.
 *
 * <p>Carried by {@link JournalEntryRequest}. The sum of all {@link #amount} values across a journal
 * must be zero — the ledger module enforces this invariant on {@link RecordJournalEntryUseCase}.
 *
 * @param accountId target {@code LedgerAccount} identifier
 * @param amount signed amount; positive values represent debits, negative values represent credits
 *     (standard accounting convention)
 * @param type explicit debit/credit marker for audit-log clarity; must be consistent with the sign
 *     of {@link #amount}
 */
public record PostingRequest(UUID accountId, Money amount, Type type) {

  /** Explicit debit/credit marker mirroring the sign of {@link PostingRequest#amount()}. */
  public enum Type {
    /** Positive posting (asset increase / liability decrease). */
    DEBIT,
    /** Negative posting (asset decrease / liability increase). */
    CREDIT
  }

  public PostingRequest {
    Objects.requireNonNull(accountId, "accountId must not be null");
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(type, "type must not be null");
  }
}
