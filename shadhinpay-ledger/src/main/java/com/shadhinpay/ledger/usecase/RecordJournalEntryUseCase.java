package com.shadhinpay.ledger.usecase;

/**
 * Records a balanced {@link JournalEntryRequest} and updates account balances atomically.
 *
 * <p><b>Caller:</b> the ledger's own transactional event listeners that consume payment-core events
 * ({@code PaymentCompletedEvent}, {@code PaymentRefundedEvent}); also called directly by the {@code
 * settlement} module for fee and adjustment postings.
 *
 * <p><b>Guarantees:</b>
 *
 * <ul>
 *   <li><b>Idempotent</b> on {@code (sourceType, sourceId)}: a duplicate submission is silently
 *       ignored, which makes Modulith event redelivery safe.
 *   <li><b>Atomic</b>: journal, postings, and per-account balance updates are written in a single
 *       database transaction.
 *   <li><b>Balanced</b>: the implementation rejects requests whose postings do not sum to zero.
 *   <li><b>Concurrency-safe</b>: balance updates use optimistic locking with bounded retries.
 * </ul>
 */
public interface RecordJournalEntryUseCase {

  /**
   * Atomically records the supplied journal entry.
   *
   * @param request the journal entry to record (must be balanced)
   */
  void execute(JournalEntryRequest request);
}
