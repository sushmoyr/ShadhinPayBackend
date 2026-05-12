package pay.conflux.backend.provisioning.usecase;

import java.util.Objects;
import java.util.UUID;
import pay.conflux.backend.provisioning.constant.Environment;

/** Result of a successful API-key validation. */
public record ValidatedApiKey(UUID apiKeyId, UUID businessId, Environment environment) {

  public ValidatedApiKey {
    Objects.requireNonNull(apiKeyId, "apiKeyId must not be null");
    Objects.requireNonNull(businessId, "businessId must not be null");
    Objects.requireNonNull(environment, "environment must not be null");
  }
}
