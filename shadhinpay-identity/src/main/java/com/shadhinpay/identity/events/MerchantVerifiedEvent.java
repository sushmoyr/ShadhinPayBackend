package com.shadhinpay.identity.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MerchantVerifiedEvent(
    UUID userId, UUID merchantProfileId, Instant occurredAt, String traceId) {

  public MerchantVerifiedEvent {
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(merchantProfileId, "merchantProfileId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(traceId, "traceId must not be null");
  }
}
