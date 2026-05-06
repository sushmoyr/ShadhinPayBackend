package com.shadhinpay.paymentcore.usecase;

import java.util.Objects;
import java.util.UUID;

/**
 * Result of a successful (or replayed-idempotent) call to {@link InitiatePaymentUseCase}.
 *
 * <p>{@link #redirectUrl} is the URL the merchant must redirect the customer to so they can
 * complete the MFS interaction. {@link #status} is the current {@code Transaction.status} (e.g.
 * {@code "INITIATED"}, {@code "PENDING"}); kept as {@code String} to defer enum coupling to Phase
 * 1.
 *
 * @param transactionId the {@code Transaction.id} created (or matched by idempotency replay)
 * @param redirectUrl URL the merchant must redirect the customer to in order to authorize the
 *     payment with the MFS
 * @param status current {@code Transaction.status} at the moment of return
 */
public record PaymentInitiationResult(UUID transactionId, String redirectUrl, String status) {

  public PaymentInitiationResult {
    Objects.requireNonNull(transactionId, "transactionId must not be null");
    Objects.requireNonNull(redirectUrl, "redirectUrl must not be null");
    Objects.requireNonNull(status, "status must not be null");
  }
}
