package pay.conflux.backend.provisioning.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Platform-supplied PARTNER-mode credentials, keyed by vendor (case-insensitive).
 *
 * <p>Bound from {@code conflux.adapters.partner-credentials.{vendor}.{key} = value} in application
 * configuration. Each vendor entry is a free-form string map carrying whatever keys the
 * corresponding adapter needs (e.g. {@code appKey}, {@code appSecret}, {@code username}).
 *
 * <p>Missing vendor entries are reported by {@link CredentialsResolver} at first use rather than at
 * boot: an unconfigured platform vendor only matters when a merchant actually points a {@code
 * VendorConfig} at it in {@code PARTNER} mode.
 */
@Configuration
@ConfigurationProperties(prefix = "conflux.adapters")
public class PartnerCredentialsConfig {

  private final Map<String, Map<String, String>> partnerCredentials = new HashMap<>();

  public Map<String, Map<String, String>> getPartnerCredentials() {
    return partnerCredentials;
  }

  /**
   * Lookup by vendor name. Returns an empty map if no entry exists — callers decide how to surface
   * the failure.
   */
  public Map<String, String> credentialsFor(String vendor) {
    if (vendor == null) {
      return Collections.emptyMap();
    }
    Map<String, String> direct = partnerCredentials.get(vendor);
    if (direct != null) {
      return direct;
    }
    Map<String, String> upper = partnerCredentials.get(vendor.toUpperCase(Locale.ROOT));
    return upper == null ? Collections.emptyMap() : upper;
  }
}
