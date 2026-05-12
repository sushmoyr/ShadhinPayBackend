package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.constant.Environment;
import pay.conflux.backend.provisioning.dto.ApiKeyDto;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.ApiKeyCacheEvictor;
import pay.conflux.backend.provisioning.usecase.GenerateApiKeyUseCase;

@ExtendWith(MockitoExtension.class)
class RotateApiKeyUseCaseImplTest {

  @Mock private ApiKeyRepository apiKeyRepository;
  @Mock private BusinessRepository businessRepository;
  @Mock private ApiKeyCacheEvictor cacheEvictor;
  @Mock private GenerateApiKeyUseCase generateApiKeyUseCase;

  @InjectMocks private RotateApiKeyUseCaseImpl useCase;

  @Test
  void execute_happyPath_revokesOldAndIssuesNew() {
    UUID businessId = UUID.randomUUID();
    UUID keyId = UUID.randomUUID();

    Business business = new Business();
    business.setId(businessId);
    business.setStatus(BusinessStatus.ACTIVE);

    ApiKey existing = new ApiKey();
    existing.setId(keyId);
    existing.setBusinessId(businessId);
    existing.setKeyHash("oldhash");
    existing.setEnvironment(Environment.TEST);
    existing.setRevoked(false);

    ApiKeyDto fresh = new ApiKeyDto();
    fresh.setId(UUID.randomUUID());
    fresh.setKey("sp_test_new");

    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
    when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(existing));
    when(generateApiKeyUseCase.execute(eq(businessId), any())).thenReturn(fresh);

    ApiKeyDto result = useCase.execute(businessId, keyId);

    assertThat(result).isSameAs(fresh);
    assertThat(existing.isRevoked()).isTrue();
    verify(apiKeyRepository).save(existing);
    verify(cacheEvictor).evictApiKeyByHash(businessId, "oldhash");
  }

  @Test
  void execute_businessInactive_throwsInvalidOperationState() {
    UUID businessId = UUID.randomUUID();
    UUID keyId = UUID.randomUUID();

    Business business = new Business();
    business.setId(businessId);
    business.setStatus(BusinessStatus.INACTIVE);
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

    assertThatThrownBy(() -> useCase.execute(businessId, keyId))
        .isInstanceOf(InvalidOperationStateException.class);
    verify(generateApiKeyUseCase, never()).execute(any(), any());
  }

  @Test
  void execute_businessMissing_throwsResourceNotFound() {
    UUID businessId = UUID.randomUUID();
    when(businessRepository.findById(businessId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(businessId, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void execute_keyBelongsToDifferentBusiness_throwsResourceNotFound() {
    UUID businessId = UUID.randomUUID();
    UUID keyId = UUID.randomUUID();

    Business business = new Business();
    business.setId(businessId);
    business.setStatus(BusinessStatus.ACTIVE);

    ApiKey existing = new ApiKey();
    existing.setId(keyId);
    existing.setBusinessId(UUID.randomUUID()); // different business
    existing.setEnvironment(Environment.TEST);

    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
    when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> useCase.execute(businessId, keyId))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
