package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.provisioning.constant.Environment;
import pay.conflux.backend.provisioning.dto.ApiKeyDto;
import pay.conflux.backend.provisioning.dto.GenerateApiKeyRequest;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.MerchantStatusPort;

@ExtendWith(MockitoExtension.class)
class GenerateApiKeyUseCaseImplTest {

  @Mock private BusinessRepository businessRepository;
  @Mock private ApiKeyRepository apiKeyRepository;
  @Mock private MerchantStatusPort merchantStatusPort;

  @InjectMocks private GenerateApiKeyUseCaseImpl useCase;

  @Test
  void execute_testKey_happyPath_returnsPlaintextOnce() {
    UUID businessId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    Business business = business(businessId, merchantId);
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
    when(apiKeyRepository.save(any(ApiKey.class)))
        .thenAnswer(
            inv -> {
              ApiKey k = inv.getArgument(0);
              k.setId(UUID.randomUUID());
              return k;
            });

    ApiKeyDto dto = useCase.execute(businessId, new GenerateApiKeyRequest("TEST", null));

    assertThat(dto.getKey()).startsWith("sp_test_");
    assertThat(dto.getKey())
        .hasSize("sp_test_".length() + GenerateApiKeyUseCaseImpl.RANDOM_SUFFIX_LENGTH);
    assertThat(dto.getEnvironment()).isEqualTo(Environment.TEST);
    assertThat(dto.getLastFour()).isEqualTo(dto.getKey().substring(dto.getKey().length() - 4));

    ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
    verify(apiKeyRepository).save(captor.capture());
    ApiKey persisted = captor.getValue();
    assertThat(persisted.getKeyHash()).isEqualTo(GenerateApiKeyUseCaseImpl.sha256Hex(dto.getKey()));
    assertThat(persisted.getKeyPrefix()).isEqualTo("sp_test_");
    assertThat(persisted.getLastFour()).isEqualTo(dto.getLastFour());
    assertThat(persisted.getEnvironment()).isEqualTo(Environment.TEST);
    assertThat(persisted.isRevoked()).isFalse();
    // SHA-256 hex is 64 chars; never equal to plaintext
    assertThat(persisted.getKeyHash()).hasSize(64).isNotEqualTo(dto.getKey());
  }

  @Test
  void execute_liveKey_whenMerchantNotActive_throwsInvalidOperationState() {
    UUID businessId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    Business business = business(businessId, merchantId);
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
    when(merchantStatusPort.isActive(merchantId)).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(businessId, new GenerateApiKeyRequest("LIVE", null)))
        .isInstanceOf(InvalidOperationStateException.class);

    verify(apiKeyRepository, never()).save(any());
  }

  @Test
  void execute_liveKey_whenMerchantActive_succeedsWithLivePrefix() {
    UUID businessId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    Business business = business(businessId, merchantId);
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
    when(merchantStatusPort.isActive(merchantId)).thenReturn(true);
    when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

    ApiKeyDto dto = useCase.execute(businessId, new GenerateApiKeyRequest("LIVE", null));

    assertThat(dto.getKey()).startsWith("sp_live_");
    assertThat(dto.getEnvironment()).isEqualTo(Environment.LIVE);
  }

  @Test
  void execute_missingBusiness_throwsResourceNotFound() {
    UUID missing = UUID.randomUUID();
    when(businessRepository.findById(missing)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(missing, new GenerateApiKeyRequest("TEST", null)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void execute_unknownEnvironment_throwsValidation() {
    UUID businessId = UUID.randomUUID();
    when(businessRepository.findById(businessId))
        .thenReturn(Optional.of(business(businessId, UUID.randomUUID())));
    assertThatThrownBy(() -> useCase.execute(businessId, new GenerateApiKeyRequest("PROD", null)))
        .isInstanceOf(ValidationException.class);
  }

  private Business business(UUID id, UUID merchantId) {
    Business b = new Business();
    b.setId(id);
    b.setMerchantId(merchantId);
    return b;
  }
}
