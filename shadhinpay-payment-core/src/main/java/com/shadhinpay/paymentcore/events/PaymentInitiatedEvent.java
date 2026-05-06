package com.shadhinpay.paymentcore.events;

import com.shadhinpay.common.money.Money;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PaymentInitiatedEvent(
    UUID transactionId,
    UUID merchantId,
    UUID businessId,
    Money amount,
    String vendor,
    String mode,
    String merchantOrderReference,
    Map<String, String> metadata,
    Instant occurredAt,
    String traceId) {

  public PaymentInitiatedEvent {
    Objects.requireNonNull(transactionId, "transactionId must not be null");
    Objects.requireNonNull(merchantId, "merchantId must not be null");
    Objects.requireNonNull(businessId, "businessId must not be null");
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(vendor, "vendor must not be null");
    Objects.requireNonNull(mode, "mode must not be null");
    Objects.requireNonNull(merchantOrderReference, "merchantOrderReference must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(traceId, "traceId must not be null");
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
