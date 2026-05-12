package pay.conflux.backend.provisioning.usecase.impl;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;
import pay.conflux.backend.provisioning.usecase.ValidateApiKeyUseCase;
import pay.conflux.backend.provisioning.usecase.ValidatedApiKey;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class ValidateApiKeyUseCaseImpl implements ValidateApiKeyUseCase {

  private final ApiKeyRepository apiKeyRepository;
  private final ApiKeyUsageTracker usageTracker;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public ValidatedApiKey execute(String plaintextKey) {
    if (plaintextKey == null || plaintextKey.isBlank()) {
      throw new UnauthorizedException("Invalid API key");
    }
    String hash = GenerateApiKeyUseCaseImpl.sha256Hex(plaintextKey);

    ApiKey apiKey =
        apiKeyRepository
            .findByKeyHashAndRevokedFalse(hash)
            .orElseThrow(() -> new UnauthorizedException("Invalid API key"));

    Instant now = clock.instant();
    if (apiKey.getExpiresAt() != null && !apiKey.getExpiresAt().isAfter(now)) {
      throw new UnauthorizedException("Invalid API key");
    }

    usageTracker.recordUsage(apiKey.getId(), now);
    return new ValidatedApiKey(apiKey.getId(), apiKey.getBusinessId(), apiKey.getEnvironment());
  }
}
