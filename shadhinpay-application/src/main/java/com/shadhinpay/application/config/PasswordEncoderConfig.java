package com.shadhinpay.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Active in every profile (including {@code openapi}). Use cases such as {@code
 * RegisterMerchantUseCaseImpl} and {@code AuthenticateUserUseCaseImpl} declare a {@link
 * PasswordEncoder} dependency, so the bean must exist even when the request-scope security chain
 * (see {@link SecurityConfig} / {@link OpenApiSecurityConfig}) is gated by profile.
 */
@Configuration
public class PasswordEncoderConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
  }
}
