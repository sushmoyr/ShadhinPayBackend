package pay.conflux.backend.provisioning.usecase.impl;

import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.SetBusinessStatusUseCase;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class SetBusinessStatusUseCaseImpl implements SetBusinessStatusUseCase {

  private final BusinessRepository businessRepository;
  private final ProvisioningMapper mapper;
  private final StringRedisTemplate redisTemplate;

  @Override
  @Transactional
  public BusinessDto execute(UUID businessId, BusinessStatus status) {
    Business business =
        businessRepository
            .findById(businessId)
            .filter(b -> !b.isDeleted())
            .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));

    business.setStatus(status);
    Business saved = businessRepository.save(business);

    if (status == BusinessStatus.INACTIVE) {
      evictApiKeyCache(businessId);
    }
    log.info("Set business id={} status={}", businessId, status);
    return mapper.toDto(saved);
  }

  private void evictApiKeyCache(UUID businessId) {
    try {
      String indexKey = ProvisioningCacheKeys.businessApiKeyIndex(businessId);
      Set<String> hashes = redisTemplate.opsForSet().members(indexKey);
      if (hashes != null) {
        for (String hash : hashes) {
          redisTemplate.delete(ProvisioningCacheKeys.apiKey(hash));
        }
      }
      redisTemplate.delete(indexKey);
    } catch (RuntimeException e) {
      log.warn("Failed to evict apikey cache for businessId={}: {}", businessId, e.getMessage());
    }
  }
}
