package pay.conflux.backend.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderConfigTest {

  @Test
  void passwordEncoder_isBCryptStrength10_andRoundTrips() {
    PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

    assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
    String hash = encoder.encode("hunter2");
    assertThat(hash).startsWith("$2a$10$");
    assertThat(encoder.matches("hunter2", hash)).isTrue();
    assertThat(encoder.matches("wrong", hash)).isFalse();
  }
}
