package com.shadhinpay.adapters.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.shadhinpay.common.money.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VendorRefundRequestTest {

  private static final UUID TXN_ID = UUID.randomUUID();
  private static final String VENDOR_TRX_ID = "vendor-trx-999";
  private static final Money AMOUNT = Money.of(50, "BDT");
  private static final String REASON = "customer requested refund";

  @Test
  void rejects_null_transactionId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new VendorRefundRequest(null, VENDOR_TRX_ID, AMOUNT, REASON))
        .withMessageContaining("transactionId");
  }

  @Test
  void rejects_null_originalVendorTrxId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new VendorRefundRequest(TXN_ID, null, AMOUNT, REASON))
        .withMessageContaining("originalVendorTrxId");
  }

  @Test
  void rejects_null_amount() {
    assertThatNullPointerException()
        .isThrownBy(() -> new VendorRefundRequest(TXN_ID, VENDOR_TRX_ID, null, REASON))
        .withMessageContaining("amount");
  }

  @Test
  void rejects_null_reason() {
    assertThatNullPointerException()
        .isThrownBy(() -> new VendorRefundRequest(TXN_ID, VENDOR_TRX_ID, AMOUNT, null))
        .withMessageContaining("reason");
  }

  @Test
  void accessors_return_constructed_values() {
    VendorRefundRequest req = new VendorRefundRequest(TXN_ID, VENDOR_TRX_ID, AMOUNT, REASON);
    assertThat(req.transactionId()).isEqualTo(TXN_ID);
    assertThat(req.originalVendorTrxId()).isEqualTo(VENDOR_TRX_ID);
    assertThat(req.amount()).isEqualTo(AMOUNT);
    assertThat(req.reason()).isEqualTo(REASON);
  }
}
