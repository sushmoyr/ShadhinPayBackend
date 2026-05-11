package pay.conflux.backend.quota.usecase;

import java.util.UUID;

/**
 * Cancels a pending {@link QuotaReservation} so the slot is returned to the merchant's pool.
 *
 * <p><b>Caller:</b> {@code payment-core} when a transaction transitions to {@code FAILED}, {@code
 * CANCELLED}, or expires before reaching {@code COMPLETED}.
 *
 * <p><b>Guarantees:</b>
 *
 * <ul>
 *   <li>Idempotent on {@code (merchantId, reservationId)}: releasing an already-released or
 *       already-confirmed reservation is a no-op.
 *   <li>No-op (with warning) for unknown reservations — never throws.
 * </ul>
 */
public interface ReleaseQuotaUseCase {

  /**
   * Releases the supplied pending reservation.
   *
   * @param merchantId merchant the reservation belongs to
   * @param reservationId handle returned by {@link ReserveQuotaUseCase}
   */
  void execute(UUID merchantId, UUID reservationId);
}
