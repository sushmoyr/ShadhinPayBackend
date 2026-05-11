package pay.conflux.backend.provisioning.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal security config for @WebMvcTest slice tests: enables method-level @PreAuthorize
 * so @WithMockUser-driven assertions exercise method security rather than URL filters, and permits
 * all HTTP-level access so unauthenticated requests reach the controller method (where they are
 * rejected by the @PreAuthorize annotation).
 */
@TestConfiguration
@EnableMethodSecurity(proxyTargetClass = true)
public class TestSliceSecurityConfig {

  @Bean
  SecurityFilterChain testSliceFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }
}
