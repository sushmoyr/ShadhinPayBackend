package com.shadhinpay.identity.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BCryptPropertyTest {

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

  @Property(tries = 100)
  void bcryptRoundTrip(
      @ForAll @StringLength(min = 8, max = 72) String password,
      @ForAll @StringLength(min = 8, max = 72) String otherPassword) {
    String hash = encoder.encode(password);

    assertThat(BCrypt.checkpw(password, hash)).isTrue();

    if (!password.equals(otherPassword)) {
      assertThat(BCrypt.checkpw(otherPassword, hash)).isFalse();
    }
  }
}
