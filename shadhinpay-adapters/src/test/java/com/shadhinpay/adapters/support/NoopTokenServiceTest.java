package com.shadhinpay.adapters.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shadhinpay.adapters.port.Vendor;
import com.shadhinpay.adapters.port.VendorCredentials;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoopTokenServiceTest {

  @Test
  void getToken_always_throws_UnsupportedOperationException() {
    NoopTokenService service = new NoopTokenService();
    VendorCredentials creds = new VendorCredentials(Map.of("key", "val"));

    assertThatThrownBy(() -> service.getToken(Vendor.MOCK, creds))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("Wave-C")
        .hasMessageContaining("Vendor=MOCK");
  }
}
