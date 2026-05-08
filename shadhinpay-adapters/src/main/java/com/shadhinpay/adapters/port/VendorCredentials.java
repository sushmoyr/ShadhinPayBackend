package com.shadhinpay.adapters.port;

import java.util.Map;
import java.util.Objects;

/**
 * Decrypted vendor credentials, supplied per-call to a {@link PaymentProvider}.
 *
 * <p><b>Lifecycle &amp; security:</b>
 *
 * <ul>
 *   <li>Instances are short-lived: built by {@code payment-core} from a {@code
 *       VendorConfigDescriptor} (provisioning) immediately before the adapter call and discarded
 *       once the call returns.
 *   <li>Never persist, cache, or pass to a logger. The {@link #toString()} override redacts every
 *       value so accidental {@code log.info("creds={}", creds)} calls cannot leak secrets.
 *   <li>The {@code values} map is defensively copied and made immutable so an adapter cannot mutate
 *       the caller's view nor accidentally hold a reference past the request scope.
 * </ul>
 *
 * @param values decrypted key/value pairs (e.g. {@code app_key}, {@code app_secret}, {@code
 *     api_key}); the exact key set is vendor-specific and documented by each adapter
 */
@SuppressWarnings("PMD.UnusedAssignment")
public record VendorCredentials(Map<String, String> values) {

  public VendorCredentials {
    Objects.requireNonNull(values, "values must not be null");
    values = Map.copyOf(values);
  }

  /**
   * Redacted string form: prints only the credential key names and a placeholder, never the actual
   * values. Safe to include in log output and exception messages.
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(80);
    sb.append("VendorCredentials{keys=[");
    boolean first = true;
    for (String key : values.keySet()) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(key);
      first = false;
    }
    sb.append("], values=***REDACTED***}");
    return sb.toString();
  }
}
