package pay.conflux.backend.common.security;

import java.util.Objects;
import java.util.UUID;

public record AuthenticatedPrincipal(
    UUID userId, UserType userType, UUID merchantId, UUID businessId, Environment environment) {

  public AuthenticatedPrincipal {
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(userType, "userType must not be null");
  }

  public enum UserType {
    MERCHANT,
    ADMIN
  }

  public enum Environment {
    TEST,
    LIVE
  }
}
