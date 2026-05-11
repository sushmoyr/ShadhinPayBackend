package pay.conflux.backend.provisioning.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.constant.Environment;
import pay.conflux.backend.provisioning.constant.Vendor;
import pay.conflux.backend.provisioning.constant.VendorMode;
import pay.conflux.backend.provisioning.dto.ApiKeySummaryDto;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.dto.BusinessSummaryDto;
import pay.conflux.backend.provisioning.dto.VendorConfigDto;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.entity.VendorConfig;

class ProvisioningMapperTest {

  private final ProvisioningMapper mapper = Mappers.getMapper(ProvisioningMapper.class);

  @Test
  void toDto_business_doesNotExposeWebhookSecret() {
    Business b = new Business();
    b.setId(UUID.randomUUID());
    b.setMerchantId(UUID.randomUUID());
    b.setName("Acme");
    b.setDisplayName("Acme");
    b.setStatus(BusinessStatus.ACTIVE);
    b.setWebhookUrl("https://h");
    b.setWebhookSecretEncrypted("ENC:should-not-be-in-dto");

    BusinessDto dto = mapper.toDto(b);

    assertThat(dto.getId()).isEqualTo(b.getId());
    assertThat(dto.getWebhookUrl()).isEqualTo("https://h");
    // The DTO has no webhookSecret field at all — guarded structurally by
    // ProvisioningMapperAuditTest.
    assertThat(dto.getStatus()).isEqualTo(BusinessStatus.ACTIVE);
  }

  @Test
  void toSummary_business_carriesNoSecretFields() {
    Business b = new Business();
    b.setId(UUID.randomUUID());
    b.setName("N");
    b.setDisplayName("D");
    b.setStatus(BusinessStatus.INACTIVE);

    BusinessSummaryDto s = mapper.toSummary(b);
    assertThat(s.getId()).isEqualTo(b.getId());
    assertThat(s.getStatus()).isEqualTo(BusinessStatus.INACTIVE);
  }

  @Test
  void toDto_vendorConfig_configuredFalseWhenNoCredentials() {
    VendorConfig vc = new VendorConfig();
    vc.setId(UUID.randomUUID());
    vc.setBusinessId(UUID.randomUUID());
    vc.setVendor(Vendor.BKASH);
    vc.setMode(VendorMode.PARTNER);
    vc.setCredentialsEncrypted(null);

    VendorConfigDto dto = mapper.toDto(vc);
    assertThat(dto.isConfigured()).isFalse();
    assertThat(dto.getMode()).isEqualTo(VendorMode.PARTNER);
  }

  @Test
  void toDto_vendorConfig_configuredTrueWhenCustomCipherStored() {
    VendorConfig vc = new VendorConfig();
    vc.setId(UUID.randomUUID());
    vc.setBusinessId(UUID.randomUUID());
    vc.setVendor(Vendor.NAGAD);
    vc.setMode(VendorMode.CUSTOM);
    vc.setCredentialsEncrypted(Map.of("ct", "ENC:vendor-cipher"));

    VendorConfigDto dto = mapper.toDto(vc);
    assertThat(dto.isConfigured()).isTrue();
  }

  @Test
  void toSummary_apiKey_excludesKeyHash() {
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setBusinessId(UUID.randomUUID());
    k.setKeyHash("DO_NOT_LEAK");
    k.setKeyPrefix("sp_test_");
    k.setLastFour("1234");
    k.setEnvironment(Environment.TEST);
    k.setRevoked(false);
    k.setLastUsedAt(Instant.parse("2026-05-11T10:00:00Z"));

    ApiKeySummaryDto s = mapper.toSummary(k);
    assertThat(s.getKeyPrefix()).isEqualTo("sp_test_");
    assertThat(s.getLastFour()).isEqualTo("1234");
    assertThat(s.getEnvironment()).isEqualTo(Environment.TEST);
    // DTO has no keyHash field — structurally enforced by ProvisioningMapperAuditTest.
  }
}
