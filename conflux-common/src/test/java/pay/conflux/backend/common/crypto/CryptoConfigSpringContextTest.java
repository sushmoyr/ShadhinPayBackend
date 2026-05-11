package pay.conflux.backend.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CryptoConfigSpringContextTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of())
          .withUserConfiguration(CryptoConfig.class);

  @Test
  void prodProfile_withoutMasterKey_failsToStart() {
    runner
        .withPropertyValues("spring.profiles.active=prod")
        .run(
            context -> {
              assertThat(context).hasFailed();
              Throwable failure = context.getStartupFailure();
              assertThat(failure)
                  .isInstanceOf(BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .rootCause()
                  .hasMessageContaining("CONFLUX_MASTER_KEY");
            });
  }

  @Test
  void stagingProfile_withInvalidKey_failsToStart() {
    runner
        .withPropertyValues("spring.profiles.active=staging", "CONFLUX_MASTER_KEY=@@@not base64@@@")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure()).hasStackTraceContaining("not valid Base64");
            });
  }

  @Test
  void prodProfile_withValidKey_startsCleanly() {
    String key = Base64.getEncoder().encodeToString(new byte[32]);
    runner
        .withPropertyValues("spring.profiles.active=prod", "CONFLUX_MASTER_KEY=" + key)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(AesGcmCipher.class);
              assertThat(context).hasSingleBean(HmacSigner.class);
            });
  }

  @Test
  void devProfile_withoutMasterKey_startsWithEphemeralKey() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(AesGcmCipher.class);
        });
  }
}
