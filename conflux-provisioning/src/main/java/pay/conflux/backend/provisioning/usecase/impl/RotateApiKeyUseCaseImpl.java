package pay.conflux.backend.provisioning.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.dto.ApiKeyDto;
import pay.conflux.backend.provisioning.dto.GenerateApiKeyRequest;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.ApiKeyCacheEvictor;
import pay.conflux.backend.provisioning.usecase.GenerateApiKeyUseCase;
import pay.conflux.backend.provisioning.usecase.RotateApiKeyUseCase;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class RotateApiKeyUseCaseImpl implements RotateApiKeyUseCase {

  private final ApiKeyRepository apiKeyRepository;
  private final BusinessRepository businessRepository;
  private final ApiKeyCacheEvictor cacheEvictor;
  private final GenerateApiKeyUseCase generateApiKeyUseCase;

  @Override
  @Transactional
  public ApiKeyDto execute(UUID businessId, UUID apiKeyId) {
    Business business =
        businessRepository
            .findById(businessId)
            .filter(b -> !b.isDeleted())
            .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));

    if (business.getStatus() != BusinessStatus.ACTIVE) {
      throw new InvalidOperationStateException(
          "API keys can only be rotated for ACTIVE businesses");
    }

    ApiKey existing =
        apiKeyRepository
            .findById(apiKeyId)
            .filter(k -> businessId.equals(k.getBusinessId()))
            .orElseThrow(() -> new ResourceNotFoundException("ApiKey", apiKeyId));

    if (!existing.isRevoked()) {
      existing.setRevoked(true);
      apiKeyRepository.save(existing);
      cacheEvictor.evictApiKeyByHash(businessId, existing.getKeyHash());
    }

    GenerateApiKeyRequest request = new GenerateApiKeyRequest();
    request.setEnvironment(existing.getEnvironment().name());
    request.setExpiresAt(existing.getExpiresAt());
    ApiKeyDto fresh = generateApiKeyUseCase.execute(businessId, request);
    log.info(
        "Rotated API key oldId={} newId={} businessId={}", apiKeyId, fresh.getId(), businessId);
    return fresh;
  }
}
