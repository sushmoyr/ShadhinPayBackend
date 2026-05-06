package com.shadhinpay.quota.usecase;

import java.util.Objects;
import java.util.UUID;

/**
 * Result of a successful soft-reservation against a merchant's monthly partner-mode quota.
 *
 * <p>Returned by {@link ReserveQuotaUseCase}. The {@link #reservationId} is the handle the caller
 * passes to {@link ConfirmQuotaUseCase} on success or {@link ReleaseQuotaUseCase} on failure;
 * {@link #status} indicates whether the impending transaction is within the free tier or will be
 * billable.
 *
 * @param reservationId opaque handle identifying this pending reservation
 * @param status tier this reservation falls into
 */
public record QuotaReservation(UUID reservationId, Status status) {

  /** Billing tier the reserved transaction falls into. */
  public enum Status {
    /** Within the free monthly allowance — no billing impact on confirm. */
    FREE,
    /** Beyond the free allowance — confirming this reservation accrues a charge. */
    BILLABLE
  }

  public QuotaReservation {
    Objects.requireNonNull(reservationId, "reservationId must not be null");
    Objects.requireNonNull(status, "status must not be null");
  }
}
