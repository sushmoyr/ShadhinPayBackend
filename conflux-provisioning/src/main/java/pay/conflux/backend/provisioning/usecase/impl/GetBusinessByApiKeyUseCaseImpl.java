package pay.conflux.backend.provisioning.usecase.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.BusinessContext;
import pay.conflux.backend.provisioning.usecase.GetBusinessByApiKeyUseCase;

/**
 * Hot-path resolver used by the global gateway filter on every authenticated inbound request.
 *
 * <p>The DB lookup is fronted by a Redis cache keyed on the SHA-256 of the supplied API key. Cache
 * writes also push the keyHash into a secondary set {@code
 * provisioning:business:{businessId}:apikeys} so that a {@code UserBlockedEvent} can evict every
 * cached key for a business in O(n) lookups rather than scanning the keyspace.
 *
 * <p>Returns only {@code BusinessContext} — no secrets, no API key plaintext, no encrypted blobs
 * ever cross this boundary.
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class GetBusinessByApiKeyUseCaseImpl implements GetBusinessByApiKeyUseCase {

  static final Duration CACHE_TTL = Duration.ofSeconds(300);

  private final ApiKeyRepository apiKeyRepository;
  private final BusinessRepository businessRepository;
  private final StringRedisTemplate redisTemplate;
  private final ApiKeyUsageTracker usageTracker;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
  public BusinessContext execute(String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new UnauthorizedException("Invalid API key");
    }
    String keyHash = GenerateApiKeyUseCaseImpl.sha256Hex(apiKey);
    String cacheKey = ProvisioningCacheKeys.apiKey(keyHash);

    BusinessContext cached = readFromCache(cacheKey);
    if (cached != null) {
      return cached;
    }

    ApiKey row =
        apiKeyRepository
            .findByKeyHashAndRevokedFalse(keyHash)
            .orElseThrow(() -> new UnauthorizedException("Invalid API key"));

    if (row.getExpiresAt() != null && !row.getExpiresAt().isAfter(java.time.Instant.now())) {
      throw new UnauthorizedException("Invalid API key");
    }

    Business business =
        businessRepository
            .findById(row.getBusinessId())
            .orElseThrow(() -> new UnauthorizedException("Invalid API key"));

    if (business.isDeleted() || business.getStatus() != BusinessStatus.ACTIVE) {
      throw new UnauthorizedException("Invalid API key");
    }

    BusinessContext context =
        new BusinessContext(
            business.getId(),
            business.getMerchantId(),
            row.getEnvironment().name(),
            business.getWebhookUrl() == null ? "" : business.getWebhookUrl());

    writeToCache(cacheKey, context, business.getId(), keyHash);
    usageTracker.recordUsage(row.getId(), java.time.Instant.now());
    return context;
  }

  private BusinessContext readFromCache(String cacheKey) {
    try {
      String raw = redisTemplate.opsForValue().get(cacheKey);
      if (raw == null) {
        return null;
      }
      return objectMapper.readValue(raw, BusinessContext.class);
    } catch (RuntimeException | JsonProcessingException e) {
      log.warn("apikey cache read failed key={}: {}", cacheKey, e.getMessage());
      return null;
    }
  }

  private void writeToCache(
      String cacheKey, BusinessContext context, UUID businessId, String keyHash) {
    try {
      String json = objectMapper.writeValueAsString(context);
      redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
      redisTemplate.opsForSet().add(ProvisioningCacheKeys.businessApiKeyIndex(businessId), keyHash);
    } catch (RuntimeException | JsonProcessingException e) {
      log.warn("apikey cache write failed key={}: {}", cacheKey, e.getMessage());
    }
  }

  /** Evicts a single API key entry. Used by key rotation / revocation flows. */
  public void evictApiKeyByHash(UUID businessId, String keyHash) {
    String key = ProvisioningCacheKeys.apiKey(keyHash);
    redisTemplate.delete(key);
    redisTemplate
        .opsForSet()
        .remove(ProvisioningCacheKeys.businessApiKeyIndex(businessId), keyHash);
  }
}
