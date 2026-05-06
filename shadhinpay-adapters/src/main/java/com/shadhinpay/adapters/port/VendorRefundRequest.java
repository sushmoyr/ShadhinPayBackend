package com.shadhinpay.adapters.port;

import com.shadhinpay.common.money.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * Input to {@link PaymentProvider#refund(VendorRefundRequest, VendorCredentials)}.
 *
 * <p>Refund amounts may be partial (less than the original transaction) but never zero or negative;
 * validation of business rules (refund window, ledger balance) happens in {@code payment-core}
 * before this DTO is built.
 *
 * @param transactionId internal ShadhinPay transaction id of the original payment
 * @param originalVendorTrxId vendor-issued id from the original successful transaction (required by
 *     every MFS to correlate the refund)
 * @param amount refund amount and currency; must be {@code &le;} the original amount
 * @param reason free-form reason recorded for audit and shown in some vendor dashboards
 */
public record VendorRefundRequest(
    UUID transactionId, String originalVendorTrxId, Money amount, String reason) {

  public VendorRefundRequest {
    Objects.requireNonNull(transactionId, "transactionId must not be null");
    Objects.requireNonNull(originalVendorTrxId, "originalVendorTrxId must not be null");
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
  }
}
