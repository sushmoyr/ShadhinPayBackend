package com.shadhinpay.paymentcore.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.shadhinpay.common.money.Money;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InitiatePaymentRequestTest {

  private static final UUID BUSINESS = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final Money AMOUNT = Money.of(new BigDecimal("500.0000"), "BDT");
  private static final String VENDOR = "BKASH";
  private static final String ORDER_REF = "ORDER-1";
  private static final String CALLBACK = "https://merchant.example/callback";
  private static final String WEBHOOK = "https://merchant.example/webhook";
  private static final Map<String, String> META = Map.of("source", "API");
  private static final String IDEMPOTENCY_KEY = "idem-key-1";

  @Test
  void rejects_null_businessId() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new InitiatePaymentRequest(
                    null, AMOUNT, VENDOR, ORDER_REF, CALLBACK, WEBHOOK, META, IDEMPOTENCY_KEY))
        .withMessageContaining("businessId");
  }

  @Test
  void rejects_null_amount() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new InitiatePaymentRequest(
                    BUSINESS, null, VENDOR, ORDER_REF, CALLBACK, WEBHOOK, META, IDEMPOTENCY_KEY))
        .withMessageContaining("amount");
  }

  @Test
  void rejects_null_vendor() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new InitiatePaymentRequest(
                    BUSINESS, AMOUNT, null, ORDER_REF, CALLBACK, WEBHOOK, META, IDEMPOTENCY_KEY))
        .withMessageContaining("vendor");
  }

  @Test
  void rejects_null_merchantOrderReference() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new InitiatePaymentRequest(
                    BUSINESS, AMOUNT, VENDOR, null, CALLBACK, WEBHOOK, META, IDEMPOTENCY_KEY))
        .withMessageContaining("merchantOrderReference");
  }

  @Test
  void rejects_null_callbackUrl() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new InitiatePaymentRequest(
                    BUSINESS, AMOUNT, VENDOR, ORDER_REF, null, WEBHOOK, META, IDEMPOTENCY_KEY))
        .withMessageContaining("callbackUrl");
  }

  @Test
  void rejects_null_webhookUrl() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new InitiatePaymentRequest(
                    BUSINESS, AMOUNT, VENDOR, ORDER_REF, CALLBACK, null, META, IDEMPOTENCY_KEY))
        .withMessageContaining("webhookUrl");
  }

  @Test
  void rejects_null_idempotencyKey() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new InitiatePaymentRequest(
                    BUSINESS, AMOUNT, VENDOR, ORDER_REF, CALLBACK, WEBHOOK, META, null))
        .withMessageContaining("idempotencyKey");
  }

  @Test
  void null_metadata_is_replaced_with_empty_map() {
    InitiatePaymentRequest req =
        new InitiatePaymentRequest(
            BUSINESS, AMOUNT, VENDOR, ORDER_REF, CALLBACK, WEBHOOK, null, IDEMPOTENCY_KEY);
    assertThat(req.metadata()).isEmpty();
  }

  @Test
  void metadata_is_defensively_copied_and_immutable() {
    Map<String, String> mutable = new HashMap<>();
    mutable.put("k", "v");
    InitiatePaymentRequest req =
        new InitiatePaymentRequest(
            BUSINESS, AMOUNT, VENDOR, ORDER_REF, CALLBACK, WEBHOOK, mutable, IDEMPOTENCY_KEY);
    mutable.put("k2", "v2");
    assertThat(req.metadata()).containsExactly(Map.entry("k", "v"));
  }

  @Test
  void equality_is_value_based() {
    InitiatePaymentRequest a =
        new InitiatePaymentRequest(
            BUSINESS, AMOUNT, VENDOR, ORDER_REF, CALLBACK, WEBHOOK, META, IDEMPOTENCY_KEY);
    InitiatePaymentRequest b =
        new InitiatePaymentRequest(
            BUSINESS, AMOUNT, VENDOR, ORDER_REF, CALLBACK, WEBHOOK, META, IDEMPOTENCY_KEY);
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }
}
