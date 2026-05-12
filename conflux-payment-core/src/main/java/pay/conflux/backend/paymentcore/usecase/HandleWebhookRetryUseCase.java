package pay.conflux.backend.paymentcore.usecase;

import java.util.UUID;

/**
 * Single-row dispatch step for the {@code WebhookOutbox} drainer.
 *
 * <p>The scheduled {@code WebhookOutboxDispatcher} polls a page of {@code PENDING} rows and hands
 * each off to {@link #execute(UUID)} on a dedicated {@code webhookExecutor} thread. The method
 * performs the HTTP POST, computes the {@code X-PGW-Signature}, persists the new {@code attempt}
 * state, and is safe to invoke concurrently across rows.
 */
public interface HandleWebhookRetryUseCase {

  /**
   * Attempts delivery of a single {@code WebhookOutbox} row. Never throws to the caller — transport
   * errors are persisted on the row as {@code lastError} with the next-attempt backoff applied.
   *
   * @param outboxId id of the {@code WebhookOutbox} row to attempt
   */
  void execute(UUID outboxId);
}
