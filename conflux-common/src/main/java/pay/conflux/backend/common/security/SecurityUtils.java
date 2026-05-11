package pay.conflux.backend.common.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pay.conflux.backend.common.security.AuthenticatedPrincipal.UserType;

public final class SecurityUtils {

  private SecurityUtils() {}

  public static Optional<AuthenticatedPrincipal> currentPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return Optional.empty();
    }
    Object principal = auth.getPrincipal();
    if (principal instanceof AuthenticatedPrincipal p) {
      return Optional.of(p);
    }
    return Optional.empty();
  }

  public static Optional<UUID> currentUserId() {
    return currentPrincipal().map(AuthenticatedPrincipal::userId);
  }

  public static Optional<UUID> currentMerchantId() {
    return currentPrincipal()
        .filter(p -> p.userType() == UserType.MERCHANT)
        .map(AuthenticatedPrincipal::merchantId);
  }

  public static Optional<UUID> currentBusinessId() {
    return currentPrincipal().map(AuthenticatedPrincipal::businessId);
  }

  public static Optional<UUID> currentAdminId() {
    return currentPrincipal()
        .filter(p -> p.userType() == UserType.ADMIN)
        .map(AuthenticatedPrincipal::userId);
  }
}
