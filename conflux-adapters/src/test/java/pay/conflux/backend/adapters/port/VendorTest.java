package pay.conflux.backend.adapters.port;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VendorTest {

  @Test
  void sslcommerzValueIsRecognized() {
    assertThat(Vendor.valueOf("SSLCOMMERZ")).isEqualTo(Vendor.SSLCOMMERZ);
  }

  @Test
  void cardinalityMatchesLockedContract() {
    // Wave A baseline = 8 vendors; Wave C sub-prompt 0 adds SSLCOMMERZ -> 9.
    // The hardcoded count is intentional: the next wave's pre-prompt must update it
    // deliberately rather than silently absorbing new values.
    assertThat(Vendor.values()).hasSize(9);
  }
}
