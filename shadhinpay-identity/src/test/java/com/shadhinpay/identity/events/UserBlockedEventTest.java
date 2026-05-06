package com.shadhinpay.identity.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserBlockedEventTest {

  private static final UUID USER = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final String REASON = "fraud";
  private static final Instant AT = Instant.parse("2026-05-05T12:00:00Z");
  private static final String TRACE = "trace-xyz";

  @Test
  void rejects_null_userId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new UserBlockedEvent(null, REASON, AT, TRACE))
        .withMessageContaining("userId");
  }

  @Test
  void rejects_null_reason() {
    assertThatNullPointerException()
        .isThrownBy(() -> new UserBlockedEvent(USER, null, AT, TRACE))
        .withMessageContaining("reason");
  }

  @Test
  void rejects_null_occurredAt() {
    assertThatNullPointerException()
        .isThrownBy(() -> new UserBlockedEvent(USER, REASON, null, TRACE))
        .withMessageContaining("occurredAt");
  }

  @Test
  void rejects_null_traceId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new UserBlockedEvent(USER, REASON, AT, null))
        .withMessageContaining("traceId");
  }

  @Test
  void equality_is_value_based() {
    UserBlockedEvent a = new UserBlockedEvent(USER, REASON, AT, TRACE);
    UserBlockedEvent b = new UserBlockedEvent(USER, REASON, AT, TRACE);
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }
}
