package com.shadhinpay.quota.usecase;

import java.util.UUID;

/**
 * Promotes a pending {@link QuotaReservation} to final usage.
 *
 * <p><b>Caller:</b> {@code payment-core} after a transaction is finalized to {@code COMPLETED}.
 *
 * <p><b>Guarantees:</b>
 *
 * <ul>
 *   <li>Idempotent on {@code (merchantId, reservationId)}: confirming an already-confirmed
 *       reservation is a no-op.
 *   <li>No-op (with warning) for unknown or expired reservations — never throws.
 * </ul>
 */
public interface ConfirmQuotaUseCase {

  /**
   * Promotes the supplied reservation from pending to final usage.
   *
   * @param merchantId merchant the reservation belongs to
   * @param reservationId handle returned by {@link ReserveQuotaUseCase}
   */
  void execute(UUID merchantId, UUID reservationId);
}
