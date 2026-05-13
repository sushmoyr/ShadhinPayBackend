package pay.conflux.backend.identity.usecase;

import java.util.UUID;
import pay.conflux.backend.common.security.AuthenticatedPrincipal;

/**
 * Resolves a JWT compact string into the data the application-bootstrap filter needs to populate
 * Spring Security. Keeps the user lookup and the {@code AdminProfile} join inside the identity
 * module so other modules never reach into {@code identity.entity} or {@code identity.repository}.
 *
 * <p>Throws {@code UnauthorizedException} on any failure mode (parse failure, expired/tampered
 * signature, unknown user id, non-{@code ACTIVE} status, or admin user with no profile). The caller
 * renders the 401 response.
 */
public interface ResolveJwtPrincipalUseCase {

  Resolved execute(String jwt);

  /**
   * Lookup result. {@link #adminTierName} is {@code null} for merchant principals and the string
   * form of {@code AdminTier} for admin principals — string-typed to avoid leaking the {@code
   * AdminTier} enum across module boundaries.
   */
  record Resolved(UUID userId, AuthenticatedPrincipal.UserType userType, String adminTierName) {}
}
