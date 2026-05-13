package pay.conflux.backend.application.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;
import pay.conflux.backend.application.security.ApiKeyAuthFilter;
import pay.conflux.backend.application.security.JwtAuthorizationFilter;
import pay.conflux.backend.identity.usecase.ResolveJwtPrincipalUseCase;
import pay.conflux.backend.provisioning.usecase.GetBusinessByApiKeyUseCase;

class SecurityConfigTest {

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class))
          .withUserConfiguration(TestFilterBeans.class, SecurityConfig.class);

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

  @Configuration
  static class TestFilterBeans {
    @Bean
    ApiKeyAuthFilter apiKeyAuthFilter() {
      return new ApiKeyAuthFilter(mock(GetBusinessByApiKeyUseCase.class), new ObjectMapper());
    }

    @Bean
    JwtAuthorizationFilter jwtAuthorizationFilter() {
      return new JwtAuthorizationFilter(mock(ResolveJwtPrincipalUseCase.class), new ObjectMapper());
    }
  }
}
