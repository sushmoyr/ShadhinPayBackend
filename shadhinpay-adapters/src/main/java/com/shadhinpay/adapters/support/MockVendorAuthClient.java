package com.shadhinpay.adapters.support;

import com.shadhinpay.adapters.port.Vendor;
import com.shadhinpay.adapters.port.VendorCredentials;
import java.time.Instant;
import org.springframework.stereotype.Component;

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
