package com.shadhinpay.adapters.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.shadhinpay.adapters.port.Vendor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {PaymentProviderRegistry.class, com.shadhinpay.adapters.mock.MockAdapter.class})
class PaymentProviderRegistryIntegrationTest {

  @Autowired private PaymentProviderRegistry registry;

  @Test
  void lookup_returnsMockAdapterForMockVendor() {
    assertThat(registry.lookup(Vendor.MOCK))
        .isInstanceOf(com.shadhinpay.adapters.mock.MockAdapter.class);
  }

  @Test
  void lookup_throwsExceptionForUnsupportedVendor() {
    org.junit.jupiter.api.Assertions.assertThrows(
        com.shadhinpay.adapters.error.MfsAdapterException.class,
        () -> registry.lookup(Vendor.BKASH));
  }
}
