package com.shadhinpay.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shadhinpay.common.security.AuthenticatedPrincipal.Environment;
import com.shadhinpay.common.security.AuthenticatedPrincipal.UserType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticatedPrincipalTest {

  @Test
  void requiresUserId() {
    assertThatThrownBy(() -> new AuthenticatedPrincipal(null, UserType.MERCHANT, null, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void requiresUserType() {
    assertThatThrownBy(() -> new AuthenticatedPrincipal(UUID.randomUUID(), null, null, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void enumsExposeAllValues() {
    assertThat(UserType.values()).containsExactly(UserType.MERCHANT, UserType.ADMIN);
    assertThat(Environment.values()).containsExactly(Environment.TEST, Environment.LIVE);
    assertThat(UserType.valueOf("ADMIN")).isEqualTo(UserType.ADMIN);
    assertThat(Environment.valueOf("TEST")).isEqualTo(Environment.TEST);
  }
}
