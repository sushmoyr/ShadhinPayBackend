package com.shadhinpay.paymentcore.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.shadhinpay.common.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentInitiatedEventTest {

  private static final UUID TXN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID MERCHANT = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private static final UUID BUSINESS = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
  private static final Money AMOUNT = Money.of(new BigDecimal("100.0000"), "BDT");
  private static final String VENDOR = "BKASH";
  private static final String MODE = "PARTNER";
  private static final String ORDER_REF = "MO-1";
  private static final Instant AT = Instant.parse("2026-05-05T12:00:00Z");
  private static final String TRACE = "trace-1";
  private static final Map<String, String> META = Map.of("source", "API");

  @Test
  void rejects_null_transactionId() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new PaymentInitiatedEvent(
                    null, MERCHANT, BUSINESS, AMOUNT, VENDOR, MODE, ORDER_REF, META, AT, TRACE))
        .withMessageContaining("transactionId");
  }

  @Test
  void rejects_null_amount() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new PaymentInitiatedEvent(
                    TXN, MERCHANT, BUSINESS, null, VENDOR, MODE, ORDER_REF, META, AT, TRACE))
        .withMessageContaining("amount");
  }

  @Test
  void rejects_null_vendor() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new PaymentInitiatedEvent(
                    TXN, MERCHANT, BUSINESS, AMOUNT, null, MODE, ORDER_REF, META, AT, TRACE))
        .withMessageContaining("vendor");
  }

  @Test
  void rejects_null_traceId() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new PaymentInitiatedEvent(
                    TXN, MERCHANT, BUSINESS, AMOUNT, VENDOR, MODE, ORDER_REF, META, AT, null))
        .withMessageContaining("traceId");
  }

  @Test
  void null_metadata_is_replaced_with_empty_map() {
    PaymentInitiatedEvent e =
        new PaymentInitiatedEvent(
            TXN, MERCHANT, BUSINESS, AMOUNT, VENDOR, MODE, ORDER_REF, null, AT, TRACE);
    assertThat(e.metadata()).isEmpty();
  }

  @Test
  void metadata_is_defensively_copied_and_immutable() {
    Map<String, String> mutable = new HashMap<>();
    mutable.put("k", "v");
    PaymentInitiatedEvent e =
        new PaymentInitiatedEvent(
            TXN, MERCHANT, BUSINESS, AMOUNT, VENDOR, MODE, ORDER_REF, mutable, AT, TRACE);
    mutable.put("k2", "v2");
    assertThat(e.metadata()).containsExactly(Map.entry("k", "v"));
  }

  @Test
  void equality_is_value_based() {
    PaymentInitiatedEvent a =
        new PaymentInitiatedEvent(
            TXN, MERCHANT, BUSINESS, AMOUNT, VENDOR, MODE, ORDER_REF, META, AT, TRACE);
    PaymentInitiatedEvent b =
        new PaymentInitiatedEvent(
            TXN, MERCHANT, BUSINESS, AMOUNT, VENDOR, MODE, ORDER_REF, META, AT, TRACE);
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }
}
