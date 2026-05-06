package com.shadhinpay.paymentcore.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.shadhinpay.common.error.ErrorCode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentFailedEventTest {

  private static final UUID TXN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID MERCHANT = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private static final UUID BUSINESS = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
  private static final String VENDOR = "BKASH";
  private static final ErrorCode CODE = ErrorCode.MFS_ADAPTER_FAILURE;
  private static final String REASON = "vendor 502";
  private static final Map<String, String> META = Map.of("attempt", "3");
  private static final Instant AT = Instant.parse("2026-05-05T12:00:00Z");
  private static final String TRACE = "trace-f";

  @Test
  void rejects_null_errorCode() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new PaymentFailedEvent(
                    TXN, MERCHANT, BUSINESS, VENDOR, null, REASON, META, AT, TRACE))
        .withMessageContaining("errorCode");
  }

  @Test
  void rejects_null_reason() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new PaymentFailedEvent(
                    TXN, MERCHANT, BUSINESS, VENDOR, CODE, null, META, AT, TRACE))
        .withMessageContaining("reason");
  }

  @Test
  void rejects_null_traceId() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new PaymentFailedEvent(
                    TXN, MERCHANT, BUSINESS, VENDOR, CODE, REASON, META, AT, null))
        .withMessageContaining("traceId");
  }

  @Test
  void null_metadata_is_replaced_with_empty_map() {
    PaymentFailedEvent e =
        new PaymentFailedEvent(TXN, MERCHANT, BUSINESS, VENDOR, CODE, REASON, null, AT, TRACE);
    assertThat(e.metadata()).isEmpty();
  }

  @Test
  void equality_is_value_based() {
    PaymentFailedEvent a =
        new PaymentFailedEvent(TXN, MERCHANT, BUSINESS, VENDOR, CODE, REASON, META, AT, TRACE);
    PaymentFailedEvent b =
        new PaymentFailedEvent(TXN, MERCHANT, BUSINESS, VENDOR, CODE, REASON, META, AT, TRACE);
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }
}
