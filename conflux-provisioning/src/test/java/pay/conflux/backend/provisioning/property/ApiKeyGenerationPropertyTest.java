package pay.conflux.backend.provisioning.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeProperty;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.constant.Environment;
import pay.conflux.backend.provisioning.dto.ApiKeyDto;
import pay.conflux.backend.provisioning.dto.GenerateApiKeyRequest;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.MerchantStatusPort;
import pay.conflux.backend.provisioning.usecase.ValidatedApiKey;
import pay.conflux.backend.provisioning.usecase.impl.ApiKeyUsageTracker;
import pay.conflux.backend.provisioning.usecase.impl.GenerateApiKeyUseCaseImpl;
import pay.conflux.backend.provisioning.usecase.impl.ValidateApiKeyUseCaseImpl;

/**
 * Invariants on the API-key generate → validate round-trip:
 *
 * <ul>
 *   <li>For 10k random (businessId, environment) pairs the SHA-256 hashes never collide.
 *   <li>Every generated key validates back to the same (businessId, environment).
 * </ul>
 */
class ApiKeyGenerationPropertyTest {

  private final Map<String, ApiKey> store = new HashMap<>();
  private final Set<String> uniqueHashes = new HashSet<>();
  private GenerateApiKeyUseCaseImpl generator;
  private ValidateApiKeyUseCaseImpl validator;

  @BeforeProperty
  void setUp() {
    store.clear();
    uniqueHashes.clear();

    BusinessRepository businessRepository = mock(BusinessRepository.class);
    ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
    MerchantStatusPort merchantStatusPort = mock(MerchantStatusPort.class);
    ApiKeyUsageTracker usageTracker = mock(ApiKeyUsageTracker.class);

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
              saved.setId(UUID.randomUUID());
              store.put(saved.getKeyHash(), saved);
              return saved;
            });
    when(apiKeyRepository.findByKeyHashAndRevokedFalse(any()))
        .thenAnswer(
            inv -> {
              String hash = inv.getArgument(0);
              ApiKey row = store.get(hash);
              return row == null || row.isRevoked() ? Optional.empty() : Optional.of(row);
            });

    generator =
        new GenerateApiKeyUseCaseImpl(businessRepository, apiKeyRepository, merchantStatusPort);
    validator = new ValidateApiKeyUseCaseImpl(apiKeyRepository, usageTracker, Clock.systemUTC());
  }

  @Provide
  Arbitrary<Environment> environments() {
    return Arbitraries.of(Environment.values());
  }

  /**
   * For each random (businessId, environment) pair, generating a key must produce a SHA-256 hash
   * that has never been seen before in this property's sample.
   */
  @Property(tries = 10_000)
  void generatedHashes_areUniqueAcrossSample(
      @ForAll @IntRange(min = 0, max = 9_999_999) int seed,
      @ForAll("environments") Environment env) {
    UUID businessId = new UUID(seed, seed);
    GenerateApiKeyRequest request = new GenerateApiKeyRequest();
    request.setEnvironment(env.name());

    ApiKeyDto created = generator.execute(businessId, request);
    String hash = sha256Hex(created.getKey());

    assertThat(uniqueHashes.add(hash))
        .as("Hash %s for plaintext %s collided with an earlier sample", hash, created.getKey())
        .isTrue();
  }

  /** validate(generate(x)) always returns x — round-trip identity. */
  @Property(tries = 500)
  void validateAfterGenerate_isIdentity(
      @ForAll @IntRange(min = 0, max = 9_999_999) int seed,
      @ForAll("environments") Environment env) {
    UUID businessId = new UUID(seed ^ 0xC0FFEEL, seed);
    GenerateApiKeyRequest request = new GenerateApiKeyRequest();
    request.setEnvironment(env.name());

    ApiKeyDto created = generator.execute(businessId, request);
    ValidatedApiKey result = validator.execute(created.getKey());

    assertThat(result.businessId()).isEqualTo(businessId);
    assertThat(result.environment()).isEqualTo(env);
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
