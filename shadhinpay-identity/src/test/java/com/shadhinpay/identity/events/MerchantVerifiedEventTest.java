package com.shadhinpay.identity.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchantVerifiedEventTest {

  private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID PROFILE = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final Instant AT = Instant.parse("2026-05-05T12:00:00Z");
  private static final String TRACE = "trace-abc";

  @Test
  void rejects_null_userId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new MerchantVerifiedEvent(null, PROFILE, AT, TRACE))
        .withMessageContaining("userId");
  }

  @Test
  void rejects_null_merchantProfileId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new MerchantVerifiedEvent(USER, null, AT, TRACE))
        .withMessageContaining("merchantProfileId");
  }

  @Test
  void rejects_null_occurredAt() {
    assertThatNullPointerException()
        .isThrownBy(() -> new MerchantVerifiedEvent(USER, PROFILE, null, TRACE))
        .withMessageContaining("occurredAt");
  }

  @Test
  void rejects_null_traceId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new MerchantVerifiedEvent(USER, PROFILE, AT, null))
        .withMessageContaining("traceId");
  }

  @Test
  void equality_is_value_based() {
    MerchantVerifiedEvent a = new MerchantVerifiedEvent(USER, PROFILE, AT, TRACE);
    MerchantVerifiedEvent b = new MerchantVerifiedEvent(USER, PROFILE, AT, TRACE);
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }
}
