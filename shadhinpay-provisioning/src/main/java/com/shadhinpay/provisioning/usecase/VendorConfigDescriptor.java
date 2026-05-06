package com.shadhinpay.provisioning.usecase;

import java.util.Map;
import java.util.Objects;

/**
 * Adapter-routing descriptor for one MFS vendor configured on a business.
 *
 * <p>Returned by {@link GetVendorConfigUseCase}. The {@code credentialsRefs} map carries <em>opaque
 * pointers</em> (e.g. KMS key aliases, secret-store handles) — never plaintext keys or tokens.
 * Resolving a pointer to a live secret happens inside the adapter layer at dispatch time.
 *
 * @param vendor vendor identifier (e.g. {@code "BKASH"}, {@code "NAGAD"}); kept as {@code String}
 *     to defer enum coupling to Phase 1
 * @param mode integration mode (e.g. {@code "PARTNER"}, {@code "CUSTOM"}); kept as {@code String}
 *     to defer enum coupling to Phase 1
 * @param credentialsRefs immutable map of opaque credential handles, never plaintext secrets
 */
public record VendorConfigDescriptor(
    String vendor, String mode, Map<String, String> credentialsRefs) {

  public VendorConfigDescriptor {
    Objects.requireNonNull(vendor, "vendor must not be null");
    Objects.requireNonNull(mode, "mode must not be null");
    credentialsRefs = credentialsRefs == null ? Map.of() : Map.copyOf(credentialsRefs);
  }
}
