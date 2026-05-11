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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;
import pay.conflux.backend.provisioning.usecase.ApiKeyCacheEvictor;

@ExtendWith(MockitoExtension.class)
class RevokeApiKeyUseCaseImplTest {

  @Mock private ApiKeyRepository apiKeyRepository;
  @Mock private ApiKeyCacheEvictor cacheEvictor;
  @InjectMocks private RevokeApiKeyUseCaseImpl useCase;

  @Test
  void execute_happyPath_revokesAndEvicts() {
    UUID businessId = UUID.randomUUID();
    UUID keyId = UUID.randomUUID();
    ApiKey existing = new ApiKey();
    existing.setId(keyId);
    existing.setBusinessId(businessId);
    existing.setKeyHash("h1");
    existing.setRevoked(false);
    when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(existing));

    useCase.execute(businessId, keyId);

    assertThat(existing.isRevoked()).isTrue();
    verify(apiKeyRepository).save(existing);
    verify(cacheEvictor).evictApiKeyByHash(businessId, "h1");
  }

  @Test
  void execute_alreadyRevoked_isIdempotent() {
    UUID businessId = UUID.randomUUID();
    UUID keyId = UUID.randomUUID();
    ApiKey existing = new ApiKey();
    existing.setId(keyId);
    existing.setBusinessId(businessId);
    existing.setKeyHash("h1");
    existing.setRevoked(true);
    when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(existing));

    useCase.execute(businessId, keyId);

    verify(apiKeyRepository, never()).save(any());
    verify(cacheEvictor, never()).evictApiKeyByHash(any(), any());
  }

  @Test
  void execute_keyForOtherBusiness_throwsResourceNotFound() {
    UUID businessId = UUID.randomUUID();
    UUID keyId = UUID.randomUUID();
    ApiKey existing = new ApiKey();
    existing.setId(keyId);
    existing.setBusinessId(UUID.randomUUID());
    when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> useCase.execute(businessId, keyId))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
