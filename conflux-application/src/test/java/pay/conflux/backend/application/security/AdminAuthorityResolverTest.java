package pay.conflux.backend.application.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import pay.conflux.backend.common.security.AuthenticatedPrincipal;

class AdminAuthorityResolverTest {

  @Test
  void merchant_resolvesToSingleMerchantAuthority() {
    assertThat(AdminAuthorityResolver.resolve(AuthenticatedPrincipal.UserType.MERCHANT, null))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("MERCHANT");
  }

  @Test
  void merchant_ignoresTier_evenWhenAccidentallyPassed() {
    // The contract says tier-for-merchant is undefined, but accidentally passing one must not leak
    // an admin authority into a merchant principal.
    assertThat(AdminAuthorityResolver.resolve(AuthenticatedPrincipal.UserType.MERCHANT, "SUPER"))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("MERCHANT");
  }

  @Test
  void viewerTier_resolvesToAdminViewerOnly() {
    assertThat(AdminAuthorityResolver.resolve(AuthenticatedPrincipal.UserType.ADMIN, "VIEWER"))
        .containsExactly(new SimpleGrantedAuthority("ADMIN_VIEWER"));
  }

  @Test
  void managerTier_inheritsViewerAndAddsManager() {
    assertThat(AdminAuthorityResolver.resolve(AuthenticatedPrincipal.UserType.ADMIN, "MANAGER"))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ADMIN_VIEWER", "ADMIN_MANAGER");
  }

  @Test
  void superTier_inheritsAllAuthoritiesIncludingSuperAdmin() {
    assertThat(AdminAuthorityResolver.resolve(AuthenticatedPrincipal.UserType.ADMIN, "SUPER"))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ADMIN_VIEWER", "ADMIN_MANAGER", "SUPER_ADMIN");
  }

  @Test
  void admin_withNullTier_throwsIllegalArgument() {
    assertThatThrownBy(
            () -> AdminAuthorityResolver.resolve(AuthenticatedPrincipal.UserType.ADMIN, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AdminTier name must not be null");
  }

  @Test
  void admin_withUnknownTier_throwsIllegalArgument() {
    assertThatThrownBy(
            () -> AdminAuthorityResolver.resolve(AuthenticatedPrincipal.UserType.ADMIN, "MEGA"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown admin tier name");
  }
}
