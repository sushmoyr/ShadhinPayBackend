package pay.conflux.backend.adapters.support;

import java.time.Instant;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;

/** Client for authenticating with a vendor and retrieving a session/bearer token. */
public interface VendorAuthClient {

  record AuthToken(String token, Instant expiresAt) {}

  /**
   * Authenticates with the vendor using the provided credentials.
   *
   * @param v target vendor
   * @param creds short-lived decrypted vendor credentials
   * @return the token and its expiration time
   */
  AuthToken authenticate(Vendor v, VendorCredentials creds);
}
