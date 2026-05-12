package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.provisioning.constant.Vendor;
import pay.conflux.backend.provisioning.constant.VendorMode;
import pay.conflux.backend.provisioning.entity.VendorConfig;
import pay.conflux.backend.provisioning.repository.VendorConfigRepository;
import pay.conflux.backend.provisioning.usecase.VendorConfigDescriptor;

@ExtendWith(MockitoExtension.class)
class GetVendorConfigUseCaseImplTest {

  @Mock private VendorConfigRepository vendorConfigRepository;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOps;
  @Mock private SetOperations<String, String> setOps;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GetVendorConfigUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetVendorConfigUseCaseImpl(vendorConfigRepository, redisTemplate, objectMapper);
  }

  @Test
  void execute_partnerMode_returnsEmptyRefs_andCaches() {
    UUID businessId = UUID.randomUUID();
    VendorConfig row = new VendorConfig();
    row.setId(UUID.randomUUID());
    row.setBusinessId(businessId);
    row.setVendor(Vendor.BKASH);
    row.setMode(VendorMode.PARTNER);

    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(redisTemplate.opsForSet()).thenReturn(setOps);
    when(valueOps.get(anyString())).thenReturn(null);
    when(vendorConfigRepository.findByBusinessIdAndVendor(businessId, Vendor.BKASH))
        .thenReturn(Optional.of(row));

    VendorConfigDescriptor descriptor = useCase.execute(businessId, "BKASH");

    assertThat(descriptor.vendor()).isEqualTo("BKASH");
    assertThat(descriptor.mode()).isEqualTo("PARTNER");
    assertThat(descriptor.credentialsRefs()).isEmpty();
    org.mockito.Mockito.verify(valueOps)
        .set(
            eq(ProvisioningCacheKeys.vendorConfig(businessId, "BKASH")),
            anyString(),
            eq(Duration.ofSeconds(300)));
    org.mockito.Mockito.verify(setOps)
        .add(ProvisioningCacheKeys.businessVendorIndex(businessId), "BKASH");
  }

  @Test
  void execute_customMode_returnsOpaqueRef() {
    UUID businessId = UUID.randomUUID();
    UUID vendorConfigId = UUID.randomUUID();
    VendorConfig row = new VendorConfig();
    row.setId(vendorConfigId);
    row.setBusinessId(businessId);
    row.setVendor(Vendor.NAGAD);
    row.setMode(VendorMode.CUSTOM);

    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(redisTemplate.opsForSet()).thenReturn(setOps);
    when(valueOps.get(anyString())).thenReturn(null);
    when(vendorConfigRepository.findByBusinessIdAndVendor(businessId, Vendor.NAGAD))
        .thenReturn(Optional.of(row));

    VendorConfigDescriptor descriptor = useCase.execute(businessId, "NAGAD");

    assertThat(descriptor.mode()).isEqualTo("CUSTOM");
    assertThat(descriptor.credentialsRefs())
        .containsEntry("ref", "vendor-credentials:" + vendorConfigId);
  }

  @Test
  void execute_cacheHit_skipsDb() throws Exception {
    UUID businessId = UUID.randomUUID();
    VendorConfigDescriptor cached =
        new VendorConfigDescriptor("UPAY", "PARTNER", java.util.Map.of());
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(ProvisioningCacheKeys.vendorConfig(businessId, "UPAY")))
        .thenReturn(objectMapper.writeValueAsString(cached));

    VendorConfigDescriptor result = useCase.execute(businessId, "UPAY");

    assertThat(result).isEqualTo(cached);
    org.mockito.Mockito.verifyNoInteractions(vendorConfigRepository);
  }

  @Test
  void execute_missingConfig_throwsResourceNotFound() {
    UUID businessId = UUID.randomUUID();
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(anyString())).thenReturn(null);
    when(vendorConfigRepository.findByBusinessIdAndVendor(any(), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(businessId, "ROCKET"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void execute_unknownVendor_throwsValidation() {
    UUID businessId = UUID.randomUUID();
    assertThatThrownBy(() -> useCase.execute(businessId, "FOO"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void execute_nullBusinessId_throwsValidation() {
    assertThatThrownBy(() -> useCase.execute(null, "BKASH"))
        .isInstanceOf(ValidationException.class);
  }
}
