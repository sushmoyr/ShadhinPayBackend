package com.shadhinpay.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Wave A baseline security configuration.
 *
 * <p>Active in every profile except {@code openapi}, which has its own permissive filter chain in
 * {@link OpenApiSecurityConfig}. This wave deliberately exposes a permissive STATELESS chain — the
 * real bearer-token authorization filter and the swap from {@code X-User-Id} header to a {@code
 * SecurityContextHolder} principal ship in Wave B. {@link EnableMethodSecurity} is wired in so
 * {@link org.springframework.security.access.prepost.PreAuthorize @PreAuthorize} annotations on
 * controller implementations are honored once Wave B populates the authentication.
 *
 * <p>{@code proxyTargetClass = true} forces CGLIB subclass proxies. JDK dynamic proxies would hide
 * the {@code @GetMapping}/{@code @PostMapping} annotations carried on the controller adapters,
 * leaving Spring MVC unable to discover any routes (resulting in 404s on every endpoint).
 */
@Configuration
@EnableMethodSecurity(proxyTargetClass = true)
@Profile("!openapi")
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }
}
