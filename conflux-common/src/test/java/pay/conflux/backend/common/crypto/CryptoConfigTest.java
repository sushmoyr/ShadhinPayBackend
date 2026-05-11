package pay.conflux.backend.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class CryptoConfigTest {

  private final CryptoConfig config = new CryptoConfig();

  @Test
  void hmacSignerBean_isCreated() {
    assertThat(config.hmacSigner()).isNotNull();
  }

  @Test
  void devProfile_acceptsMissingMasterKey() {
    MockEnvironment env = new MockEnvironment();
    byte[] key = CryptoConfig.loadMasterKey(env, null);
    assertThat(key).hasSize(32);
  }

  @Test
  void devProfile_acceptsBlankMasterKey() {
    MockEnvironment env = new MockEnvironment();
    byte[] key = CryptoConfig.loadMasterKey(env, "   ");
    assertThat(key).hasSize(32);
  }

  @Test
  void prodProfile_failsFastWhenMasterKeyMissing() {
    MockEnvironment env = new MockEnvironment().withProperty("a", "b");
    env.setActiveProfiles("prod");
    assertThatThrownBy(() -> CryptoConfig.loadMasterKey(env, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CONFLUX_MASTER_KEY");
  }

  @Test
  void stagingProfile_failsFastWhenMasterKeyMissing() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("staging");
    assertThatThrownBy(() -> CryptoConfig.loadMasterKey(env, ""))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void invalidBase64_isRejected() {
    MockEnvironment env = new MockEnvironment();
    assertThatThrownBy(() -> CryptoConfig.loadMasterKey(env, "@@@not base64@@@"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not valid Base64");
  }

  @Test
  void wrongLengthDecoded_isRejected() {
    MockEnvironment env = new MockEnvironment();
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
    assertThatThrownBy(() -> CryptoConfig.loadMasterKey(env, shortKey))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  void validBase64Key_isAccepted() {
    MockEnvironment env = new MockEnvironment();
    String validKey = Base64.getEncoder().encodeToString(new byte[32]);
    byte[] decoded = CryptoConfig.loadMasterKey(env, validKey);
    assertThat(decoded).hasSize(32);
  }

  @Test
  void aesGcmCipherBean_buildsFromEnvironment() {
    MockEnvironment env = new MockEnvironment();
    AesGcmCipher cipher = config.aesGcmCipher(env);
    String ct = cipher.encrypt("x", "p");
    assertThat(cipher.decrypt(ct, "p")).isEqualTo("x");
  }
}
