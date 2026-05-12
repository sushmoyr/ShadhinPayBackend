package pay.conflux.backend.provisioning.usecase.impl;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;

/**
 * Off-thread writer for {@code ApiKey.lastUsedAt}. Validation hits this asynchronously so the
 * authentication hot path doesn't block on a write.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyUsageTracker {

  private final ApiKeyRepository apiKeyRepository;

  @Async("provisioningEventExecutor")
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordUsage(UUID apiKeyId, Instant usedAt) {
    try {
      apiKeyRepository.markLastUsedAt(apiKeyId, usedAt);
    } catch (RuntimeException e) {
      log.warn("Failed to record API key usage id={}: {}", apiKeyId, e.getMessage());
    }
  }
}
