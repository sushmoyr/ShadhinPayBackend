package com.shadhinpay.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.shadhinpay.common.security.AuthenticatedPrincipal.Environment;
import com.shadhinpay.common.security.AuthenticatedPrincipal.UserType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityUtilsTest {

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void emptyContext_returnsEmpty() {
    assertThat(SecurityUtils.currentPrincipal()).isEmpty();
    assertThat(SecurityUtils.currentUserId()).isEmpty();
    assertThat(SecurityUtils.currentMerchantId()).isEmpty();
    assertThat(SecurityUtils.currentBusinessId()).isEmpty();
    assertThat(SecurityUtils.currentAdminId()).isEmpty();
  }

  @Test
  void merchantPrincipal_exposesIdsAndHidesAdmin() {
    UUID userId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    UUID businessId = UUID.randomUUID();
    setPrincipal(
        new AuthenticatedPrincipal(
            userId, UserType.MERCHANT, merchantId, businessId, Environment.LIVE));

    assertThat(SecurityUtils.currentUserId()).contains(userId);
    assertThat(SecurityUtils.currentMerchantId()).contains(merchantId);
    assertThat(SecurityUtils.currentBusinessId()).contains(businessId);
    assertThat(SecurityUtils.currentAdminId()).isEmpty();
  }

  @Test
  void adminPrincipal_exposesAdminIdAndHidesMerchant() {
    UUID userId = UUID.randomUUID();
    setPrincipal(new AuthenticatedPrincipal(userId, UserType.ADMIN, null, null, null));

    assertThat(SecurityUtils.currentAdminId()).contains(userId);
    assertThat(SecurityUtils.currentMerchantId()).isEmpty();
    assertThat(SecurityUtils.currentBusinessId()).isEmpty();
  }

  @Test
  void nonMatchingPrincipalType_isIgnored() {
    var auth = new UsernamePasswordAuthenticationToken("someStringPrincipal", null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
    assertThat(SecurityUtils.currentPrincipal()).isEmpty();
  }

  private static void setPrincipal(AuthenticatedPrincipal principal) {
    var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }
}
