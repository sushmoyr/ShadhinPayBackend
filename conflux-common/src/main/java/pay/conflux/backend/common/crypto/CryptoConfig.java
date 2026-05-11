package pay.conflux.backend.common.crypto;

import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class CryptoConfig {

  static final String MASTER_KEY_ENV = "CONFLUX_MASTER_KEY";
  private static final Set<String> NON_DEV_PROFILES = Set.of("prod", "production", "staging");

  @Bean
  public AesGcmCipher aesGcmCipher(Environment environment) {
    byte[] key = loadMasterKey(environment, environment.getProperty(MASTER_KEY_ENV));
    return new AesGcmCipher(key);
  }

  @Bean
  public HmacSigner hmacSigner() {
    return new HmacSigner();
  }

  static byte[] loadMasterKey(Environment environment, String envValue) {
    boolean nonDev = isNonDevProfile(environment);
    if (envValue == null || envValue.isBlank()) {
      if (nonDev) {
        throw new IllegalStateException(
            MASTER_KEY_ENV + " environment variable is required in non-dev profiles");
      }
      byte[] devKey = new byte[AesGcmCipher.KEY_BYTES];
      java.util.Arrays.fill(devKey, (byte) 0x2A);
      return devKey;
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(envValue.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(MASTER_KEY_ENV + " is not valid Base64", e);
    }
    if (decoded.length != AesGcmCipher.KEY_BYTES) {
      throw new IllegalStateException(
          MASTER_KEY_ENV + " must decode to 32 bytes (AES-256), got " + decoded.length);
    }
    return decoded;
  }

  private static boolean isNonDevProfile(Environment environment) {
    if (environment == null) {
      return false;
    }
    for (String profile : environment.getActiveProfiles()) {
      if (NON_DEV_PROFILES.contains(profile.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }
}
