package pay.conflux.backend.provisioning.usecase.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.provisioning.constant.Environment;
import pay.conflux.backend.provisioning.dto.ApiKeyDto;
import pay.conflux.backend.provisioning.dto.GenerateApiKeyRequest;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.GenerateApiKeyUseCase;
import pay.conflux.backend.provisioning.usecase.MerchantStatusPort;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class GenerateApiKeyUseCaseImpl implements GenerateApiKeyUseCase {

  static final int RANDOM_SUFFIX_LENGTH = 32;
  private static final char[] ALPHABET =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

  private final BusinessRepository businessRepository;
  private final ApiKeyRepository apiKeyRepository;
  private final MerchantStatusPort merchantStatusPort;
  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  @Transactional
  public ApiKeyDto execute(UUID businessId, GenerateApiKeyRequest request) {
    Business business =
        businessRepository
            .findById(businessId)
            .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));

    Environment environment = parseEnvironment(request.getEnvironment());
    if (environment == Environment.LIVE && !merchantStatusPort.isActive(business.getMerchantId())) {
      throw new InvalidOperationStateException("LIVE API keys require an ACTIVE merchant status");
    }

    String suffix = randomSuffix();
    String prefix = environment.keyPrefix();
    String plaintext = prefix + suffix;
    String keyHash = sha256Hex(plaintext);
    String lastFour = plaintext.substring(plaintext.length() - 4);

    ApiKey apiKey = new ApiKey();
    apiKey.setBusinessId(businessId);
    apiKey.setKeyHash(keyHash);
    apiKey.setKeyPrefix(prefix);
    apiKey.setLastFour(lastFour);
    apiKey.setEnvironment(environment);
    apiKey.setRevoked(false);
    apiKey.setExpiresAt(request.getExpiresAt());
    ApiKey saved = apiKeyRepository.save(apiKey);

    log.info(
        "Generated API key id={} businessId={} environment={} prefix={} lastFour={}",
        saved.getId(),
        businessId,
        environment,
        prefix,
        lastFour);

    return new ApiKeyDto(
        saved.getId(), businessId, plaintext, prefix, lastFour, environment, saved.getExpiresAt());
  }

  private String randomSuffix() {
    char[] out = new char[RANDOM_SUFFIX_LENGTH];
    for (int i = 0; i < out.length; i++) {
      out[i] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
    }
    return new String(out);
  }

  static String sha256Hex(String plaintext) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(plaintext.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static Environment parseEnvironment(String value) {
    try {
      return Environment.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Unknown environment: " + value);
    }
  }
}
