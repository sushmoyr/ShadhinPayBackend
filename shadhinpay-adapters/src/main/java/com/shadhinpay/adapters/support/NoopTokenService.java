package com.shadhinpay.adapters.support;

import com.shadhinpay.adapters.port.Vendor;
import com.shadhinpay.adapters.port.VendorCredentials;
import org.springframework.stereotype.Component;

/**
 * Placeholder {@link TokenService} active in Phase 1 Wave A — only the {@code MockAdapter} (which
 * does not need vendor tokens) is in play, so any caller is a programmer error. The real
 * Redis-backed implementation arrives in Wave C and will replace this bean.
 */
@Component
public class NoopTokenService implements TokenService {

  @Override
  public String getToken(Vendor vendor, VendorCredentials creds) {
    throw new UnsupportedOperationException(
        "TokenService is not yet wired up; Wave-C adapters will provide a real implementation. "
            + "Vendor="
            + vendor);
  }
}
