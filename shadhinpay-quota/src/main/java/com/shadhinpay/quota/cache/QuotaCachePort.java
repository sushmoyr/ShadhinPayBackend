package com.shadhinpay.quota.cache;

import java.util.UUID;

public interface QuotaCachePort {

  /**
   * Creates a pending reservation for the given merchant and period.
   *
   * @param merchantId merchant ID
   * @param period the billing period (YYYY-MM)
   * @return the generated reservation ID string
   */
  String reservePending(UUID merchantId, String period);

  /**
   * Deletes a pending reservation.
   *
   * @param merchantId merchant ID
   * @param period the billing period (YYYY-MM)
   * @param reservationId the reservation ID
   * @return true if the reservation was deleted, false otherwise
   */
  boolean releasePending(UUID merchantId, String period, String reservationId);

  /**
   * Promotes a pending reservation to final usage atomically.
   *
   * @param merchantId merchant ID
   * @param period the billing period (YYYY-MM)
   * @param reservationId the reservation ID
   * @return true if the reservation was confirmed, false if it wasn't found (no-op)
   */
  boolean confirmReservation(UUID merchantId, String period, String reservationId);

  /**
   * Gets the final usage count for the given merchant and period.
   *
   * @param merchantId merchant ID
   * @param period the billing period (YYYY-MM)
   * @return the final usage count, or 0 if not found
   */
  int getFinalCount(UUID merchantId, String period);

  /**
   * Counts the number of pending reservations for the given merchant and period.
   *
   * @param merchantId merchant ID
   * @param period the billing period (YYYY-MM)
   * @return the number of pending reservations
   */
  int countPending(UUID merchantId, String period);
}
