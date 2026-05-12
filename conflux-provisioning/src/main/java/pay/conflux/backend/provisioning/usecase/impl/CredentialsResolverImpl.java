package pay.conflux.backend.provisioning.usecase.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pay.conflux.backend.common.crypto.AesGcmCipher;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.provisioning.config.PartnerCredentialsConfig;
import pay.conflux.backend.provisioning.constant.EncryptionPurpose;
import pay.conflux.backend.provisioning.constant.Vendor;
import pay.conflux.backend.provisioning.constant.VendorMode;
import pay.conflux.backend.provisioning.entity.VendorConfig;
import pay.conflux.backend.provisioning.repository.VendorConfigRepository;
import pay.conflux.backend.provisioning.usecase.CredentialsResolver;

/**
 * Resolves the live credential map for a vendor configured on a business.
 *
 * <p>Decryption happens server-side and the resulting map is never logged. PARTNER-mode lookups
 * fall through to {@link PartnerCredentialsConfig} so platform-supplied keys are sourced from
 * deployment configuration, not stored per-tenant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CredentialsResolverImpl implements CredentialsResolver {

  private static final TypeReference<Map<String, String>> STRING_MAP =
      new TypeReference<Map<String, String>>() {};

  private final VendorConfigRepository vendorConfigRepository;
  private final AesGcmCipher cipher;
  private final ObjectMapper objectMapper;
  private final PartnerCredentialsConfig partnerCredentialsConfig;

  @Override
  public Map<String, String> resolveCredentials(UUID businessId, String vendor) {
    Vendor vendorEnum = parseVendor(vendor);
    VendorConfig row =
        vendorConfigRepository
            .findByBusinessIdAndVendor(businessId, vendorEnum)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "VendorConfig", businessId + ":" + vendorEnum.name()));

    if (row.getMode() == VendorMode.PARTNER) {
      Map<String, String> creds = partnerCredentialsConfig.credentialsFor(vendorEnum.name());
      if (creds == null || creds.isEmpty()) {
        throw new IllegalStateException(
            "PARTNER credentials not configured for vendor: " + vendorEnum.name());
      }
      return Map.copyOf(creds);
    }

    Map<String, String> encrypted = row.getCredentialsEncrypted();
    String ct = encrypted == null ? null : encrypted.get("ct");
    if (ct == null || ct.isBlank()) {
      throw new IllegalStateException(
          "CUSTOM VendorConfig is missing ciphertext for businessId="
              + businessId
              + " vendor="
              + vendorEnum.name());
    }
    String plaintextJson = cipher.decrypt(ct, EncryptionPurpose.VENDOR_CREDENTIALS);
    try {
      return Map.copyOf(objectMapper.readValue(plaintextJson, STRING_MAP));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Decrypted credentials are not a valid JSON object", e);
    }
  }

  private static Vendor parseVendor(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("vendor is required");
    }
    try {
      return Vendor.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Unknown vendor: " + value);
    }
  }
}
