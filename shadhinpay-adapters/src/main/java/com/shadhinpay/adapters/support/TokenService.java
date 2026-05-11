package com.shadhinpay.adapters.support;

import com.shadhinpay.adapters.port.Vendor;
import com.shadhinpay.adapters.port.VendorCredentials;

/**
 * Centralised vendor session-token cache.
 *
 * <p>Looks up a valid token in Redis for {@code (vendor, credentials)}; on miss or expiry, calls
 * the vendor's auth endpoint, caches the result with a TTL matching vendor policy, and returns the
 * new token. Wave A provides {@link RedisTokenService} (Redis-backed, double-checked distributed
 * lock); Wave C vendor adapters consume this interface as-is.
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
