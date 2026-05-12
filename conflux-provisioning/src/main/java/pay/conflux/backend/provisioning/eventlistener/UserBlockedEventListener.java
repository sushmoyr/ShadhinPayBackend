package pay.conflux.backend.provisioning.eventlistener;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pay.conflux.backend.identity.events.UserBlockedEvent;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.impl.ProvisioningCacheKeys;

/**
 * On user-block, deactivates every business the user owns and evicts the matching Redis cache
 * entries via the per-business secondary indexes. Reading the indexes (rather than SCANning the
 * whole keyspace) keeps eviction O(n) over the user's own keys.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserBlockedEventListener {

  private final BusinessRepository businessRepository;
  private final StringRedisTemplate redisTemplate;

  @ApplicationModuleListener
  public void on(UserBlockedEvent event) {
    List<Business> businesses = businessRepository.findByMerchantIdAndDeletedFalse(event.userId());
    for (Business business : businesses) {
      business.setStatus(BusinessStatus.INACTIVE);
      businessRepository.save(business);
      evictCachesFor(business.getId());
    }
    log.info(
        "UserBlockedEvent userId={} traceId={} — deactivated {} business(es) and flushed caches",
        event.userId(),
        event.traceId(),
        businesses.size());
  }

  private void evictCachesFor(UUID businessId) {
    String apiKeyIndex = ProvisioningCacheKeys.businessApiKeyIndex(businessId);
    Set<String> apiKeyHashes = readSet(apiKeyIndex);
    for (String keyHash : apiKeyHashes) {
      redisTemplate.delete(ProvisioningCacheKeys.apiKey(keyHash));
    }
    redisTemplate.delete(apiKeyIndex);

    String vendorIndex = ProvisioningCacheKeys.businessVendorIndex(businessId);
    Set<String> vendors = readSet(vendorIndex);
    for (String vendor : vendors) {
      redisTemplate.delete(ProvisioningCacheKeys.vendorConfig(businessId, vendor));
    }
    redisTemplate.delete(vendorIndex);
  }

  private Set<String> readSet(String key) {
    Set<String> members = redisTemplate.opsForSet().members(key);
    return members == null ? Collections.emptySet() : members;
  }
}
