package com.shadhinpay.adapters.support;

import com.shadhinpay.adapters.port.Vendor;
import com.shadhinpay.adapters.port.VendorCredentials;

/**
 * Centralised vendor session-token cache.
 *
 * <p>Looks up a valid token in Redis for {@code (vendor, credentials)}; on miss or expiry, calls
 * the vendor's auth endpoint, caches the result with a TTL matching vendor policy, and returns the
 * new token. Implementation lands in Phase 1 Wave C alongside the first real adapter; Wave A uses
 * {@link NoopTokenService}.
 */
public interface TokenService {

  /**
   * Returns a valid bearer / session token for the given vendor.
   *
   * @param vendor target vendor
   * @param creds short-lived decrypted credentials used to mint the token if a refresh is needed
   * @return a valid token string
   */
  String getToken(Vendor vendor, VendorCredentials creds);
}
