package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.crypto.AesGcmCipher;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.provisioning.config.PartnerCredentialsConfig;
import pay.conflux.backend.provisioning.constant.EncryptionPurpose;
import pay.conflux.backend.provisioning.constant.Vendor;
import pay.conflux.backend.provisioning.constant.VendorMode;
import pay.conflux.backend.provisioning.entity.VendorConfig;
import pay.conflux.backend.provisioning.repository.VendorConfigRepository;

@ExtendWith(MockitoExtension.class)
class CredentialsResolverImplTest {

  @Mock private VendorConfigRepository vendorConfigRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private AesGcmCipher cipher;
  private PartnerCredentialsConfig partnerCredentialsConfig;
  private CredentialsResolverImpl resolver;

  @BeforeEach
  void setUp() {
    byte[] devKey = new byte[32];
    Arrays.fill(devKey, (byte) 0x2A);
    cipher = new AesGcmCipher(devKey);
    partnerCredentialsConfig = new PartnerCredentialsConfig();
    resolver =
        new CredentialsResolverImpl(
            vendorConfigRepository, cipher, objectMapper, partnerCredentialsConfig);
  }

  @Test
  void resolveCredentials_partnerMode_returnsPlatformCreds() {
    UUID businessId = UUID.randomUUID();
    VendorConfig row = new VendorConfig();
    row.setBusinessId(businessId);
    row.setVendor(Vendor.BKASH);
    row.setMode(VendorMode.PARTNER);

    partnerCredentialsConfig
        .getPartnerCredentials()
        .put("BKASH", Map.of("appKey", "platform-app-key", "appSecret", "platform-app-secret"));

    when(vendorConfigRepository.findByBusinessIdAndVendor(businessId, Vendor.BKASH))
        .thenReturn(Optional.of(row));

    Map<String, String> creds = resolver.resolveCredentials(businessId, "BKASH");

    assertThat(creds)
        .containsEntry("appKey", "platform-app-key")
        .containsEntry("appSecret", "platform-app-secret");
  }

  @Test
  void resolveCredentials_partnerMode_missingPlatformConfig_throws() {
    UUID businessId = UUID.randomUUID();
    VendorConfig row = new VendorConfig();
    row.setBusinessId(businessId);
    row.setVendor(Vendor.ROCKET);
    row.setMode(VendorMode.PARTNER);
    when(vendorConfigRepository.findByBusinessIdAndVendor(businessId, Vendor.ROCKET))
        .thenReturn(Optional.of(row));

    assertThatThrownBy(() -> resolver.resolveCredentials(businessId, "ROCKET"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PARTNER credentials not configured");
  }

  @Test
  void resolveCredentials_customMode_decryptsBlob() throws Exception {
    UUID businessId = UUID.randomUUID();
    UUID vendorConfigId = UUID.randomUUID();
    Map<String, String> plaintextMap =
        Map.of("appKey", "merchant-app-key", "appSecret", "merchant-app-secret");
    String plaintextJson = objectMapper.writeValueAsString(plaintextMap);
    String ciphertext = cipher.encrypt(plaintextJson, EncryptionPurpose.VENDOR_CREDENTIALS);

    VendorConfig row = new VendorConfig();
    row.setId(vendorConfigId);
    row.setBusinessId(businessId);
    row.setVendor(Vendor.NAGAD);
    row.setMode(VendorMode.CUSTOM);
    row.setCredentialsEncrypted(Map.of("ct", ciphertext));

    when(vendorConfigRepository.findByBusinessIdAndVendor(businessId, Vendor.NAGAD))
        .thenReturn(Optional.of(row));

    Map<String, String> result = resolver.resolveCredentials(businessId, "NAGAD");

    assertThat(result).containsAllEntriesOf(plaintextMap);
  }

  @Test
  void resolveCredentials_customMode_missingCiphertext_throws() {
    UUID businessId = UUID.randomUUID();
    VendorConfig row = new VendorConfig();
    row.setBusinessId(businessId);
    row.setVendor(Vendor.UPAY);
    row.setMode(VendorMode.CUSTOM);
    row.setCredentialsEncrypted(Map.of());
    when(vendorConfigRepository.findByBusinessIdAndVendor(businessId, Vendor.UPAY))
        .thenReturn(Optional.of(row));

    assertThatThrownBy(() -> resolver.resolveCredentials(businessId, "UPAY"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing ciphertext");
  }

  @Test
  void resolveCredentials_missingRow_throwsResourceNotFound() {
    UUID businessId = UUID.randomUUID();
    when(vendorConfigRepository.findByBusinessIdAndVendor(businessId, Vendor.STRIPE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> resolver.resolveCredentials(businessId, "STRIPE"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void resolveCredentials_unknownVendor_throwsValidation() {
    UUID businessId = UUID.randomUUID();
    assertThatThrownBy(() -> resolver.resolveCredentials(businessId, "FOO"))
        .isInstanceOf(ValidationException.class);
  }
}
