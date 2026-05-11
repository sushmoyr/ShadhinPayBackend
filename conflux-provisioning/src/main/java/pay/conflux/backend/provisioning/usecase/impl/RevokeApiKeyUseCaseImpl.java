package pay.conflux.backend.provisioning.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;
import pay.conflux.backend.provisioning.usecase.ApiKeyCacheEvictor;
import pay.conflux.backend.provisioning.usecase.RevokeApiKeyUseCase;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class RevokeApiKeyUseCaseImpl implements RevokeApiKeyUseCase {

  private final ApiKeyRepository apiKeyRepository;
  private final ApiKeyCacheEvictor cacheEvictor;

  @Override
  @Transactional
  public void execute(UUID businessId, UUID apiKeyId) {
    ApiKey apiKey =
        apiKeyRepository
            .findById(apiKeyId)
            .filter(k -> businessId.equals(k.getBusinessId()))
            .orElseThrow(() -> new ResourceNotFoundException("ApiKey", apiKeyId));

    if (apiKey.isRevoked()) {
      return;
    }
    apiKey.setRevoked(true);
    apiKeyRepository.save(apiKey);
    cacheEvictor.evictApiKeyByHash(businessId, apiKey.getKeyHash());
    log.info("Revoked API key id={} businessId={}", apiKeyId, businessId);
  }
}
