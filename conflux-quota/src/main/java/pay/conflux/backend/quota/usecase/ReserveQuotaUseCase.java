package pay.conflux.backend.quota.usecase;

import java.util.UUID;

/**
 * Reserves one slot of monthly partner-mode quota for a merchant.
 *
 * <p><b>Caller:</b> {@code payment-core} during the pre-flight phase of {@code
 * InitiatePaymentUseCase}, only when {@code mode == PARTNER}. {@code CUSTOM} mode skips quota
 * entirely.
 *
 * <p><b>Guarantees:</b>
 *
 * <ul>
 *   <li>Atomic increment of a Redis pending counter — safe under high concurrency.
 *   <li>Fail-open: Redis outages must yield a {@link QuotaReservation} with status {@link
 *       QuotaReservation.Status#FREE} and a logged warning, never an exception.
 *   <li>Reservations created here must be settled by exactly one of {@link ConfirmQuotaUseCase} or
 *       {@link ReleaseQuotaUseCase}; an unreconciled reservation older than 30 minutes is cleaned
 *       up by a background job.
 * </ul>
 */
public interface ReserveQuotaUseCase {

  /**
   * Creates a pending reservation for the merchant's current monthly quota window.
   *
   * @param merchantId merchant whose quota is being reserved
   * @return a {@link QuotaReservation} carrying the handle and tier; never {@code null}
   */
  QuotaReservation execute(UUID merchantId);
}
