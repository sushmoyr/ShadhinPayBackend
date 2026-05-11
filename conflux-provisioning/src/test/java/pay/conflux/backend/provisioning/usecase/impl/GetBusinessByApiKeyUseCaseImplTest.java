package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.constant.Environment;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.BusinessContext;

@ExtendWith(MockitoExtension.class)
class GetBusinessByApiKeyUseCaseImplTest {

  @Mock private ApiKeyRepository apiKeyRepository;
  @Mock private BusinessRepository businessRepository;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOps;
  @Mock private SetOperations<String, String> setOps;
  @Mock private ApiKeyUsageTracker usageTracker;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private GetBusinessByApiKeyUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new GetBusinessByApiKeyUseCaseImpl(
            apiKeyRepository, businessRepository, redisTemplate, usageTracker, objectMapper);
  }

  @Test
  void execute_cacheHit_skipsDbAndUsageTracker() throws Exception {
    String plaintext = "sp_test_cachehit___________________________";
    String hash = GenerateApiKeyUseCaseImpl.sha256Hex(plaintext);
    UUID businessId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    BusinessContext cached =
        new BusinessContext(businessId, merchantId, "TEST", "https://hook.example/cb");
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(ProvisioningCacheKeys.apiKey(hash)))
        .thenReturn(objectMapper.writeValueAsString(cached));

    BusinessContext result = useCase.execute(plaintext);

    assertThat(result).isEqualTo(cached);
    verifyNoInteractions(apiKeyRepository, businessRepository, usageTracker);
  }

  @Test
  void execute_cacheMiss_loadsFromDbAndWritesCache() throws Exception {
    String plaintext = "sp_live_dbmiss_____________________________";
    String hash = GenerateApiKeyUseCaseImpl.sha256Hex(plaintext);
    UUID businessId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();

    ApiKey apiKey = new ApiKey();
    apiKey.setId(UUID.randomUUID());
    apiKey.setBusinessId(businessId);
    apiKey.setEnvironment(Environment.LIVE);

    Business business = new Business();
    business.setId(businessId);
    business.setMerchantId(merchantId);
    business.setStatus(BusinessStatus.ACTIVE);
    business.setWebhookUrl("https://merchant.example/webhook");

    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(redisTemplate.opsForSet()).thenReturn(setOps);
    when(valueOps.get(anyString())).thenReturn(null);
    when(apiKeyRepository.findByKeyHashAndRevokedFalse(hash)).thenReturn(Optional.of(apiKey));
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

    BusinessContext result = useCase.execute(plaintext);

    assertThat(result.businessId()).isEqualTo(businessId);
    assertThat(result.merchantId()).isEqualTo(merchantId);
    assertThat(result.environment()).isEqualTo("LIVE");
    assertThat(result.webhookUrl()).isEqualTo("https://merchant.example/webhook");

    verify(valueOps)
        .set(eq(ProvisioningCacheKeys.apiKey(hash)), anyString(), eq(Duration.ofSeconds(300)));
    verify(setOps).add(ProvisioningCacheKeys.businessApiKeyIndex(businessId), hash);
    verify(usageTracker, times(1)).recordUsage(eq(apiKey.getId()), any(Instant.class));
  }

  @Test
  void execute_cacheMissAndApiKeyMissing_throwsUnauthorized() {
    String plaintext = "sp_test_unknown____________________________";
    String hash = GenerateApiKeyUseCaseImpl.sha256Hex(plaintext);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(ProvisioningCacheKeys.apiKey(hash))).thenReturn(null);
    when(apiKeyRepository.findByKeyHashAndRevokedFalse(hash)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(plaintext)).isInstanceOf(UnauthorizedException.class);
    verifyNoInteractions(usageTracker);
  }

  @Test
  void execute_businessInactive_throwsUnauthorized() {
    String plaintext = "sp_test_inactive___________________________";
    String hash = GenerateApiKeyUseCaseImpl.sha256Hex(plaintext);
    UUID businessId = UUID.randomUUID();

    ApiKey apiKey = new ApiKey();
    apiKey.setId(UUID.randomUUID());
    apiKey.setBusinessId(businessId);
    apiKey.setEnvironment(Environment.TEST);

    Business business = new Business();
    business.setId(businessId);
    business.setMerchantId(UUID.randomUUID());
    business.setStatus(BusinessStatus.INACTIVE);

    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(anyString())).thenReturn(null);
    when(apiKeyRepository.findByKeyHashAndRevokedFalse(hash)).thenReturn(Optional.of(apiKey));
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

    assertThatThrownBy(() -> useCase.execute(plaintext)).isInstanceOf(UnauthorizedException.class);
    verifyNoInteractions(usageTracker);
    verify(setOps, never()).add(anyString(), anyString());
  }

  @Test
  void execute_blankKey_throwsUnauthorized() {
    assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(UnauthorizedException.class);
    assertThatThrownBy(() -> useCase.execute("")).isInstanceOf(UnauthorizedException.class);
    verifyNoInteractions(redisTemplate, apiKeyRepository, businessRepository, usageTracker);
  }

  @Test
  void execute_expiredKey_throwsUnauthorized() {
    String plaintext = "sp_test_expired____________________________";
    String hash = GenerateApiKeyUseCaseImpl.sha256Hex(plaintext);
    UUID businessId = UUID.randomUUID();
    ApiKey apiKey = new ApiKey();
    apiKey.setId(UUID.randomUUID());
    apiKey.setBusinessId(businessId);
    apiKey.setEnvironment(Environment.TEST);
    apiKey.setExpiresAt(Instant.EPOCH);

    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(anyString())).thenReturn(null);
    when(apiKeyRepository.findByKeyHashAndRevokedFalse(hash)).thenReturn(Optional.of(apiKey));

    assertThatThrownBy(() -> useCase.execute(plaintext)).isInstanceOf(UnauthorizedException.class);
    verifyNoInteractions(usageTracker);
  }

  @Test
  void execute_nullWebhookUrl_defaultsToEmptyString() {
    String plaintext = "sp_test_nullhook___________________________";
    String hash = GenerateApiKeyUseCaseImpl.sha256Hex(plaintext);
    UUID businessId = UUID.randomUUID();

    ApiKey apiKey = new ApiKey();
    apiKey.setId(UUID.randomUUID());
    apiKey.setBusinessId(businessId);
    apiKey.setEnvironment(Environment.TEST);

    Business business = new Business();
    business.setId(businessId);
    business.setMerchantId(UUID.randomUUID());
    business.setStatus(BusinessStatus.ACTIVE);
    business.setWebhookUrl(null);

    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(redisTemplate.opsForSet()).thenReturn(setOps);
    when(valueOps.get(anyString())).thenReturn(null);
    when(apiKeyRepository.findByKeyHashAndRevokedFalse(hash)).thenReturn(Optional.of(apiKey));
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));

    BusinessContext result = useCase.execute(plaintext);
    assertThat(result.webhookUrl()).isEmpty();
  }
}
