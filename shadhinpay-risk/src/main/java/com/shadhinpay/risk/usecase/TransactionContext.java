package com.shadhinpay.risk.usecase;

import com.shadhinpay.common.money.Money;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot of a transaction handed to the risk engine for synchronous evaluation.
 *
 * <p>Built by {@code payment-core} during the pre-flight phase of {@code InitiatePaymentUseCase}
 * and consumed by {@link EvaluateTransactionUseCase}. Every field is required except {@code
 * metadata}, which defaults to an empty map. PII fields (phone, email, ip) are passed by value so
 * the risk module can match against blacklists and velocity counters without reaching into another
 * feature's data store.
 *
 * @param merchantId merchant whose transaction is being evaluated
 * @param amount transaction amount and currency
 * @param vendor vendor identifier (e.g. {@code "BKASH"}); kept as {@code String} to defer enum
 *     coupling to Phase 1
 * @param customerPhone end-customer phone number (E.164)
 * @param customerEmail end-customer email address
 * @param ip end-customer source IP address
 * @param metadata immutable bag of contextual attributes (e.g. {@code "device_id"}, {@code
 *     "channel"})
 */
public record TransactionContext(
    UUID merchantId,
    Money amount,
    String vendor,
    String customerPhone,
    String customerEmail,
    String ip,
    Map<String, String> metadata) {

  public TransactionContext {
    Objects.requireNonNull(merchantId, "merchantId must not be null");
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(vendor, "vendor must not be null");
    Objects.requireNonNull(customerPhone, "customerPhone must not be null");
    Objects.requireNonNull(customerEmail, "customerEmail must not be null");
    Objects.requireNonNull(ip, "ip must not be null");
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
