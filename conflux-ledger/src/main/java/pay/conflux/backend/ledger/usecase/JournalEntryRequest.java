package pay.conflux.backend.ledger.usecase;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Idempotent request to record a journal entry composed of balanced postings.
 *
 * <p>Consumed by {@link RecordJournalEntryUseCase}. The pair {@code (sourceType, sourceId)} acts as
 * the natural idempotency key — re-submitting the same pair is a no-op even after retries or
 * Modulith event redelivery.
 *
 * @param sourceType the kind of business event being recorded (e.g. {@code "PAYMENT"}, {@code
 *     "REFUND"}, {@code "SETTLEMENT"}, {@code "FEE"}, {@code "ADJUSTMENT"}); kept as {@code String}
 *     to avoid coupling the contract to ledger internals
 * @param sourceId identifier of the source record in the originating module (typically the
 *     transaction id)
 * @param description short human-readable label persisted on the journal entry for audit
 * @param postings list of postings whose signed amounts must sum to zero (defensive copy held)
 * @param occurredAt business-event timestamp; the ledger persists this as {@code
 *     JournalEntry.occurredAt} for reporting purposes
 */
public record JournalEntryRequest(
    String sourceType,
    String sourceId,
    String description,
    List<PostingRequest> postings,
    Instant occurredAt) {

  public JournalEntryRequest {
    Objects.requireNonNull(sourceType, "sourceType must not be null");
    Objects.requireNonNull(sourceId, "sourceId must not be null");
    Objects.requireNonNull(description, "description must not be null");
    Objects.requireNonNull(postings, "postings must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    if (postings.isEmpty()) {
      throw new IllegalArgumentException("postings must not be empty");
    }
    postings = List.copyOf(postings);
  }
}
