package pay.conflux.backend.paymentcore.events;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import pay.conflux.backend.common.money.Money;

@SuppressWarnings("PMD.UnusedAssignment")
public record PaymentCompletedEvent(
    UUID transactionId,
    UUID merchantId,
    UUID businessId,
    Money amount,
    String vendor,
    String mode,
    String merchantOrderReference,
    Map<String, String> metadata,
    Instant occurredAt,
    String traceId,
    String vendorTransactionId,
    Money platformFee) {

  public PaymentCompletedEvent {
    Objects.requireNonNull(transactionId, "transactionId must not be null");
    Objects.requireNonNull(merchantId, "merchantId must not be null");
    Objects.requireNonNull(businessId, "businessId must not be null");
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(vendor, "vendor must not be null");
    Objects.requireNonNull(mode, "mode must not be null");
    Objects.requireNonNull(merchantOrderReference, "merchantOrderReference must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(traceId, "traceId must not be null");
    Objects.requireNonNull(vendorTransactionId, "vendorTransactionId must not be null");
    Objects.requireNonNull(platformFee, "platformFee must not be null");
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
