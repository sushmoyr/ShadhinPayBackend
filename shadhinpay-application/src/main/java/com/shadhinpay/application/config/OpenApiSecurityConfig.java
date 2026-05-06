package com.shadhinpay.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permissive Spring Security configuration used <strong>only</strong> when the {@code openapi}
 * Spring profile is active. This profile is activated by the {@code -Popenapi} Maven profile in
 * {@code shadhinpay-application/pom.xml}, where the {@code spring-boot:start} mojo boots the
 * application solely so that {@code springdoc-openapi-maven-plugin} can scrape {@code /v3/api-docs}
 * and emit {@code target/openapi/openapi.json}.
 *
 * <p>This bean is NEVER active in dev / staging / prod profiles — the real security configuration
 * lands in Phase 1 alongside the tenant API-key middleware.
 */
@Configuration
@Profile("openapi")
public class OpenApiSecurityConfig {

  @Bean
  public SecurityFilterChain openApiSecurityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }
}
