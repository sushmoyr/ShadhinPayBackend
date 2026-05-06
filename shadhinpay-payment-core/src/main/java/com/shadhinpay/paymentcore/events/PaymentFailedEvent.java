package com.shadhinpay.paymentcore.events;

import com.shadhinpay.common.error.ErrorCode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PaymentFailedEvent(
    UUID transactionId,
    UUID merchantId,
    UUID businessId,
    String vendor,
    ErrorCode errorCode,
    String reason,
    Map<String, String> metadata,
    Instant occurredAt,
    String traceId) {

  public PaymentFailedEvent {
    Objects.requireNonNull(transactionId, "transactionId must not be null");
    Objects.requireNonNull(merchantId, "merchantId must not be null");
    Objects.requireNonNull(businessId, "businessId must not be null");
    Objects.requireNonNull(vendor, "vendor must not be null");
    Objects.requireNonNull(errorCode, "errorCode must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(traceId, "traceId must not be null");
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
