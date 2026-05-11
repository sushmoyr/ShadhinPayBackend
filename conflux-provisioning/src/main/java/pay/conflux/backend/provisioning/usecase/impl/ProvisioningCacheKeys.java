package pay.conflux.backend.provisioning.usecase.impl;

import java.util.UUID;

/**
 * Centralized Redis key derivation for the provisioning module. Keeping this in one place is the
 * difference between "evict on user-block" working and a stale-token security incident.
 */
public final class ProvisioningCacheKeys {

  public static final String API_KEY_PREFIX = "provisioning:apikey:";
  public static final String VENDOR_CONFIG_PREFIX = "provisioning:vendorconfig:";
  public static final String BUSINESS_INDEX_PREFIX = "provisioning:business:";
  public static final String BUSINESS_APIKEY_INDEX_SUFFIX = ":apikeys";
  public static final String BUSINESS_VENDOR_INDEX_SUFFIX = ":vendorconfigs";

  private ProvisioningCacheKeys() {}

  public static String apiKey(String keyHash) {
    return API_KEY_PREFIX + keyHash;
  }

  public static String vendorConfig(UUID businessId, String vendor) {
    return VENDOR_CONFIG_PREFIX + businessId + ":" + vendor;
  }

  public static String businessApiKeyIndex(UUID businessId) {
    return BUSINESS_INDEX_PREFIX + businessId + BUSINESS_APIKEY_INDEX_SUFFIX;
  }

  public static String businessVendorIndex(UUID businessId) {
    return BUSINESS_INDEX_PREFIX + businessId + BUSINESS_VENDOR_INDEX_SUFFIX;
  }
}
