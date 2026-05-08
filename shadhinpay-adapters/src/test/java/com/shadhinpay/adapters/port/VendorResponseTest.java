package com.shadhinpay.adapters.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.shadhinpay.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class VendorResponseTest {

  @Test
  void rejects_null_status() {
    assertThatNullPointerException()
        .isThrownBy(() -> new VendorResponse(null, "vendor-001", null, null, null))
        .withMessageContaining("status");
  }

  @Test
  void accessors_return_constructed_values() {
    VendorResponse resp =
        new VendorResponse(
            VendorStatus.INITIATED, "v-trx-1", "https://pay.example.com", "{}", null);
    assertThat(resp.status()).isEqualTo(VendorStatus.INITIATED);
    assertThat(resp.vendorTrxId()).isEqualTo("v-trx-1");
    assertThat(resp.redirectUrl()).isEqualTo("https://pay.example.com");
    assertThat(resp.rawResponse()).isEqualTo("{}");
    assertThat(resp.errorCode()).isNull();
  }

  @Test
  void failed_response_carries_error_code() {
    VendorResponse resp =
        new VendorResponse(VendorStatus.FAILED, null, null, "{\"error\":\"declined\"}", ErrorCode.MFS_ADAPTER_FAILURE);
    assertThat(resp.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(resp.errorCode()).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
    assertThat(resp.vendorTrxId()).isNull();
    assertThat(resp.redirectUrl()).isNull();
  }

  @Test
  void all_vendor_statuses_are_valid_for_response() {
    for (VendorStatus status : VendorStatus.values()) {
      VendorResponse resp = new VendorResponse(status, null, null, null, null);
      assertThat(resp.status()).isEqualTo(status);
    }
  }
}
