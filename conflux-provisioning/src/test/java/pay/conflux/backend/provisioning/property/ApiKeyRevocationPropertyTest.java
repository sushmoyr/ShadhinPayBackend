package pay.conflux.backend.provisioning.property;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeProperty;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.constant.Environment;
import pay.conflux.backend.provisioning.dto.ApiKeyDto;
import pay.conflux.backend.provisioning.dto.GenerateApiKeyRequest;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.ApiKeyCacheEvictor;
import pay.conflux.backend.provisioning.usecase.MerchantStatusPort;
import pay.conflux.backend.provisioning.usecase.impl.ApiKeyUsageTracker;
import pay.conflux.backend.provisioning.usecase.impl.GenerateApiKeyUseCaseImpl;
import pay.conflux.backend.provisioning.usecase.impl.RevokeApiKeyUseCaseImpl;
import pay.conflux.backend.provisioning.usecase.impl.ValidateApiKeyUseCaseImpl;

/** Exhaustive: after revoke(), validate(plaintext) must throw UnauthorizedException. */
class ApiKeyRevocationPropertyTest {

  private final Map<String, ApiKey> hashStore = new HashMap<>();
  private final Map<UUID, ApiKey> idStore = new HashMap<>();
  private GenerateApiKeyUseCaseImpl generator;
  private ValidateApiKeyUseCaseImpl validator;
  private RevokeApiKeyUseCaseImpl revoker;

  @BeforeProperty
  void setUp() {
    hashStore.clear();
    idStore.clear();

    BusinessRepository businessRepository = mock(BusinessRepository.class);
    ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
    MerchantStatusPort merchantStatusPort = mock(MerchantStatusPort.class);
    ApiKeyUsageTracker usageTracker = mock(ApiKeyUsageTracker.class);
    ApiKeyCacheEvictor cacheEvictor = mock(ApiKeyCacheEvictor.class);
    doNothing().when(cacheEvictor).evictApiKeyByHash(any(UUID.class), any(String.class));

    when(merchantStatusPort.isActive(any(UUID.class))).thenReturn(true);
    when(businessRepository.findById(any(UUID.class)))
        .thenAnswer(
            inv -> {
              UUID businessId = inv.getArgument(0);
              Business b = new Business();
              b.setId(businessId);
              b.setMerchantId(UUID.randomUUID());
              b.setStatus(BusinessStatus.ACTIVE);
              return Optional.of(b);
            });
    when(apiKeyRepository.save(any(ApiKey.class)))
        .thenAnswer(
            inv -> {
              ApiKey saved = inv.getArgument(0);
              if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
              }
              hashStore.put(saved.getKeyHash(), saved);
              idStore.put(saved.getId(), saved);
              return saved;
            });
    when(apiKeyRepository.findByKeyHashAndRevokedFalse(any()))
        .thenAnswer(
            inv -> {
              String hash = inv.getArgument(0);
              ApiKey row = hashStore.get(hash);
              return row == null || row.isRevoked() ? Optional.empty() : Optional.of(row);
            });
    when(apiKeyRepository.findById(any(UUID.class)))
        .thenAnswer(inv -> Optional.ofNullable(idStore.get(inv.getArgument(0))));

    generator =
        new GenerateApiKeyUseCaseImpl(businessRepository, apiKeyRepository, merchantStatusPort);
    validator = new ValidateApiKeyUseCaseImpl(apiKeyRepository, usageTracker, Clock.systemUTC());
    revoker = new RevokeApiKeyUseCaseImpl(apiKeyRepository, cacheEvictor);
  }

  @Provide
  Arbitrary<Environment> environments() {
    return Arbitraries.of(Environment.values());
  }

  @Property(tries = 500)
  void revokedKey_validationThrowsUnauthorized(
      @ForAll @IntRange(min = 0, max = 9_999_999) int seed,
      @ForAll("environments") Environment env) {
    UUID businessId = new UUID(seed, seed ^ 0xDEADBEEFL);
    GenerateApiKeyRequest request = new GenerateApiKeyRequest();
    request.setEnvironment(env.name());

    ApiKeyDto created = generator.execute(businessId, request);
    revoker.execute(businessId, created.getId());

    assertThatThrownBy(() -> validator.execute(created.getKey()))
        .isInstanceOf(UnauthorizedException.class);
  }
}
