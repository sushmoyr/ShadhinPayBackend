package com.shadhinpay.adapters.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.shadhinpay.adapters.port.Vendor;
import com.shadhinpay.adapters.port.VendorCredentials;
import com.shadhinpay.adapters.port.VendorPaymentRequest;
import com.shadhinpay.adapters.port.VendorRefundRequest;
import com.shadhinpay.adapters.port.VendorResponse;
import com.shadhinpay.adapters.port.VendorStatus;
import com.shadhinpay.common.error.ErrorCode;
import com.shadhinpay.common.money.Money;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockAdapterTest {

  private final MockAdapter adapter = new MockAdapter();
  private final VendorCredentials creds = new VendorCredentials(Map.of("key", "val"));

  @Test
  void initiate_withSuccessMetadata_returnsCompleted() {
    VendorPaymentRequest req =
        new VendorPaymentRequest(
            UUID.randomUUID(),
            Money.of(new BigDecimal("100.00"), "BDT"),
            "REF1",
            "http://cb",
            Map.of("mock_outcome", "success"));

    VendorResponse res = adapter.initiate(req, creds);
    assertThat(res.status()).isEqualTo(VendorStatus.COMPLETED);
    assertThat(res.vendorTrxId()).isEqualTo("MOCK-" + req.transactionId());
  }

  @Test
  void initiate_withFailMetadata_returnsFailed() {
    VendorPaymentRequest req =
        new VendorPaymentRequest(
            UUID.randomUUID(),
            Money.of(new BigDecimal("100.00"), "BDT"),
            "REF1",
            "http://cb",
            Map.of("mock_outcome", "fail"));

    VendorResponse res = adapter.initiate(req, creds);
    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
  }

  @Test
  void initiate_withCancelMetadata_returnsCancelled() {
    VendorPaymentRequest req =
        new VendorPaymentRequest(
            UUID.randomUUID(),
            Money.of(new BigDecimal("100.00"), "BDT"),
            "REF1",
            "http://cb",
            Map.of("mock_outcome", "cancel"));

    VendorResponse res = adapter.initiate(req, creds);
    assertThat(res.status()).isEqualTo(VendorStatus.CANCELLED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
  }

  @Test
  void initiate_withInsufficientFundsMetadata_returnsFailedWithSpecificError() {
    VendorPaymentRequest req =
        new VendorPaymentRequest(
            UUID.randomUUID(),
            Money.of(new BigDecimal("100.00"), "BDT"),
            "REF1",
            "http://cb",
            Map.of("mock_outcome", "insufficient_funds"));

    VendorResponse res = adapter.initiate(req, creds);
    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
  }

  @Test
  void initiate_withNoMetadata_returnsInitiated() {
    VendorPaymentRequest req =
        new VendorPaymentRequest(
            UUID.randomUUID(),
            Money.of(new BigDecimal("100.00"), "BDT"),
            "REF1",
            "http://cb",
            Map.of());

    VendorResponse res = adapter.initiate(req, creds);
    assertThat(res.status()).isEqualTo(VendorStatus.INITIATED);
    assertThat(res.redirectUrl())
        .isEqualTo("https://mock.shadhinpay.local/pay/MOCK-" + req.transactionId());
  }

  @Test
  void queryStatus_deterministicRules() {
    assertThat(adapter.queryStatus("trx-pending", creds).status())
        .isEqualTo(VendorStatus.INITIATED);
    assertThat(adapter.queryStatus("trx-completed", creds).status())
        .isEqualTo(VendorStatus.COMPLETED);
    assertThat(adapter.queryStatus("trx-failed", creds).status()).isEqualTo(VendorStatus.FAILED);
    assertThat(adapter.queryStatus("trx-other", creds).status()).isEqualTo(VendorStatus.COMPLETED);
    assertThat(adapter.queryStatus(null, creds).status()).isEqualTo(VendorStatus.COMPLETED);
  }

  @Test
  void refund_returnsCompletedByDefault() {
    VendorRefundRequest req =
        new VendorRefundRequest(
            UUID.randomUUID(), "origTrx", Money.of(new BigDecimal("50.00"), "BDT"), "Reason");
    VendorResponse res = adapter.refund(req, creds);
    assertThat(res.status()).isEqualTo(VendorStatus.COMPLETED);
    assertThat(res.vendorTrxId()).isEqualTo("REFUND-" + req.transactionId());
  }

  @Test
  void refund_withFailMetadata_returnsFailed() {
    VendorRefundRequest req =
        new VendorRefundRequest(
            UUID.randomUUID(),
            "origTrx",
            Money.of(new BigDecimal("50.00"), "BDT"),
            "mock_refund_outcome=fail");

    VendorResponse res = adapter.refund(req, creds);
    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
  }

  @Test
  void supports_mockVendorOnly() {
    assertThat(adapter.supports(Vendor.MOCK)).isTrue();
    assertThat(adapter.supports(Vendor.BKASH)).isFalse();
    assertThat(adapter.supports(Vendor.NAGAD)).isFalse();
  }
}
