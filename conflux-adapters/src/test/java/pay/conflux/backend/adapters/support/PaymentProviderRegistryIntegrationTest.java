package pay.conflux.backend.adapters.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pay.conflux.backend.adapters.port.Vendor;

@SpringBootTest(
    classes = {PaymentProviderRegistry.class, pay.conflux.backend.adapters.mock.MockAdapter.class})
class PaymentProviderRegistryIntegrationTest {

  @Autowired private PaymentProviderRegistry registry;

  @Test
  void lookup_returnsMockAdapterForMockVendor() {
    assertThat(registry.lookup(Vendor.MOCK))
        .isInstanceOf(pay.conflux.backend.adapters.mock.MockAdapter.class);
  }

  @Test
  void lookup_throwsExceptionForUnsupportedVendor() {
    org.junit.jupiter.api.Assertions.assertThrows(
        pay.conflux.backend.adapters.error.MfsAdapterException.class,
        () -> registry.lookup(Vendor.BKASH));
  }
}
