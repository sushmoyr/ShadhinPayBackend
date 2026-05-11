package pay.conflux.backend.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

class SecurityConfigTest {

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class))
          .withUserConfiguration(SecurityConfig.class);

  @Test
  void securityFilterChain_isRegistered_inDefaultProfile() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(SecurityFilterChain.class);
        });
  }

  @Test
  void securityFilterChain_isNotRegistered_inOpenapiProfile() {
    contextRunner
        .withPropertyValues("spring.profiles.active=openapi")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean("securityFilterChain");
            });
  }
}
