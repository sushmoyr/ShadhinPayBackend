package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.provisioning.constant.Environment;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;
import pay.conflux.backend.provisioning.usecase.ValidatedApiKey;

@ExtendWith(MockitoExtension.class)
class ValidateApiKeyUseCaseImplTest {

  @Mock private ApiKeyRepository apiKeyRepository;
  @Mock private ApiKeyUsageTracker usageTracker;

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-11T10:00:00Z"), ZoneOffset.UTC);

  @Test
  void execute_hit_returnsBusinessAndEnvironment_andRecordsUsageAsync() {
    String plaintext = "sp_test_abcdef0123456789abcdef0123456789xY";
    String hash = GenerateApiKeyUseCaseImpl.sha256Hex(plaintext);
    UUID apiKeyId = UUID.randomUUID();
    UUID businessId = UUID.randomUUID();

    ApiKey apiKey = new ApiKey();
    apiKey.setId(apiKeyId);
    apiKey.setBusinessId(businessId);
    apiKey.setEnvironment(Environment.TEST);
    when(apiKeyRepository.findByKeyHashAndRevokedFalse(hash)).thenReturn(Optional.of(apiKey));

    ValidateApiKeyUseCaseImpl useCase =
        new ValidateApiKeyUseCaseImpl(apiKeyRepository, usageTracker, clock);

    ValidatedApiKey result = useCase.execute(plaintext);

    assertThat(result.apiKeyId()).isEqualTo(apiKeyId);
    assertThat(result.businessId()).isEqualTo(businessId);
    assertThat(result.environment()).isEqualTo(Environment.TEST);
    verify(usageTracker).recordUsage(eq(apiKeyId), any(Instant.class));
  }

  @Test
  void execute_miss_throwsUnauthorized() {
    String plaintext = "sp_live_unknown_key_xxxxxxxxxxxxxxxxxxxxxx";
    when(apiKeyRepository.findByKeyHashAndRevokedFalse(
            GenerateApiKeyUseCaseImpl.sha256Hex(plaintext)))
        .thenReturn(Optional.empty());

    ValidateApiKeyUseCaseImpl useCase =
        new ValidateApiKeyUseCaseImpl(apiKeyRepository, usageTracker, clock);

    assertThatThrownBy(() -> useCase.execute(plaintext)).isInstanceOf(UnauthorizedException.class);
    verify(usageTracker, never()).recordUsage(any(), any());
  }

  @Test
  void execute_blankKey_throwsUnauthorized() {
    ValidateApiKeyUseCaseImpl useCase =
        new ValidateApiKeyUseCaseImpl(apiKeyRepository, usageTracker, clock);
    assertThatThrownBy(() -> useCase.execute("")).isInstanceOf(UnauthorizedException.class);
    assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void execute_expiredKey_throwsUnauthorized() {
    String plaintext = "sp_test_expired_aaaaaaaaaaaaaaaaaaaaaaaaaa";
    String hash = GenerateApiKeyUseCaseImpl.sha256Hex(plaintext);
    ApiKey apiKey = new ApiKey();
    apiKey.setId(UUID.randomUUID());
    apiKey.setBusinessId(UUID.randomUUID());
    apiKey.setEnvironment(Environment.TEST);
    apiKey.setExpiresAt(Instant.parse("2026-05-10T00:00:00Z"));
    when(apiKeyRepository.findByKeyHashAndRevokedFalse(hash)).thenReturn(Optional.of(apiKey));

    ValidateApiKeyUseCaseImpl useCase =
        new ValidateApiKeyUseCaseImpl(apiKeyRepository, usageTracker, clock);

    assertThatThrownBy(() -> useCase.execute(plaintext)).isInstanceOf(UnauthorizedException.class);
  }
}
