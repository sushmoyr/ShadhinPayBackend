package com.shadhinpay.paymentcore.events;

import com.shadhinpay.common.money.Money;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PaymentRefundedEvent(
    UUID transactionId,
    UUID originalTransactionId,
    Money amount,
    Map<String, String> metadata,
    Instant occurredAt,
    String traceId) {

  public PaymentRefundedEvent {
    Objects.requireNonNull(transactionId, "transactionId must not be null");
    Objects.requireNonNull(originalTransactionId, "originalTransactionId must not be null");
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(traceId, "traceId must not be null");
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
