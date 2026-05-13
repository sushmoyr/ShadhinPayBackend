package pay.conflux.backend.application.security;

import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import pay.conflux.backend.common.security.AuthenticatedPrincipal;

/**
 * Resolves the four-string authority namespace ({@code MERCHANT}, {@code ADMIN_VIEWER}, {@code
 * ADMIN_MANAGER}, {@code SUPER_ADMIN}) per {@code DOCS/features/identity/TECH_SPEC.md §4.3}. Tier
 * inheritance is baked in here so callers never enumerate it.
 *
 * <p>The {@code adminTierName} is string-typed so this resolver does not import the {@code
 * AdminTier} enum across the identity-module boundary — the JWT principal resolver in identity
 * passes the {@code AdminTier} name through as a string.
 */
public final class AdminAuthorityResolver {

  public static final String AUTHORITY_MERCHANT = "MERCHANT";
  public static final String AUTHORITY_ADMIN_VIEWER = "ADMIN_VIEWER";
  public static final String AUTHORITY_ADMIN_MANAGER = "ADMIN_MANAGER";
  public static final String AUTHORITY_SUPER_ADMIN = "SUPER_ADMIN";

  private static final String TIER_VIEWER = "VIEWER";
  private static final String TIER_MANAGER = "MANAGER";
  private static final String TIER_SUPER = "SUPER";

  private AdminAuthorityResolver() {}

  public static List<GrantedAuthority> resolve(
      AuthenticatedPrincipal.UserType userType, String adminTierName) {
    if (userType == AuthenticatedPrincipal.UserType.MERCHANT) {
      return List.of(new SimpleGrantedAuthority(AUTHORITY_MERCHANT));
    }
    if (adminTierName == null) {
      throw new IllegalArgumentException(
          "AdminTier name must not be null for ADMIN user (schema FK guarantees presence)");
    }
    return switch (adminTierName) {
      case TIER_VIEWER -> List.of(new SimpleGrantedAuthority(AUTHORITY_ADMIN_VIEWER));
      case TIER_MANAGER ->
          List.of(
              new SimpleGrantedAuthority(AUTHORITY_ADMIN_VIEWER),
              new SimpleGrantedAuthority(AUTHORITY_ADMIN_MANAGER));
      case TIER_SUPER ->
          List.of(
              new SimpleGrantedAuthority(AUTHORITY_ADMIN_VIEWER),
              new SimpleGrantedAuthority(AUTHORITY_ADMIN_MANAGER),
              new SimpleGrantedAuthority(AUTHORITY_SUPER_ADMIN));
      default -> throw new IllegalArgumentException("Unknown admin tier name: " + adminTierName);
    };
  }
}
