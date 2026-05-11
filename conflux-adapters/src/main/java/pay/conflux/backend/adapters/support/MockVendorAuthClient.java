package pay.conflux.backend.adapters.support;

import java.time.Instant;
import org.springframework.stereotype.Component;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;

/** Mock implementation of {@link VendorAuthClient}. */
@Component
public class MockVendorAuthClient implements VendorAuthClient {

  @Override
  public AuthToken authenticate(Vendor v, VendorCredentials creds) {
    if (v != Vendor.MOCK) {
      throw new UnsupportedOperationException("MockVendorAuthClient only supports MOCK vendor");
    }
    return new AuthToken("mock-token", Instant.now().plusSeconds(3600));
  }
}
