package pay.conflux.backend.paymentcore.usecase;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import pay.conflux.backend.common.money.Money;

/**
 * Request envelope for {@link InitiatePaymentUseCase}.
 *
 * <p>The {@code idempotencyKey} is required and cooperates with the Redis-backed idempotency store:
 * two requests that share {@code (businessId, idempotencyKey)} resolve to the same {@link
 * PaymentInitiationResult} without producing a second {@code Transaction} row.
 *
 * @param businessId business owning this payment (resolved from API key by the gateway filter)
 * @param amount transaction amount and currency
 * @param vendor MFS vendor identifier (e.g. {@code "BKASH"}); kept as {@code String} to defer enum
 *     coupling to Phase 1
 * @param merchantOrderReference merchant-supplied order id, surfaced back via webhooks
 * @param callbackUrl URL the customer is redirected to after the MFS interaction
 * @param webhookUrl URL the merchant's server is notified at on terminal state changes
 * @param metadata immutable bag of merchant-supplied attributes echoed back on every notification
 * @param idempotencyKey caller-supplied key from the {@code X-Idempotency-Key} header
 */
@SuppressWarnings("PMD.UnusedAssignment")
public record InitiatePaymentRequest(
    UUID businessId,
    Money amount,
    String vendor,
    String merchantOrderReference,
    String callbackUrl,
    String webhookUrl,
    Map<String, String> metadata,
    String idempotencyKey) {

  public InitiatePaymentRequest {
    Objects.requireNonNull(businessId, "businessId must not be null");
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(vendor, "vendor must not be null");
    Objects.requireNonNull(merchantOrderReference, "merchantOrderReference must not be null");
    Objects.requireNonNull(callbackUrl, "callbackUrl must not be null");
    Objects.requireNonNull(webhookUrl, "webhookUrl must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
