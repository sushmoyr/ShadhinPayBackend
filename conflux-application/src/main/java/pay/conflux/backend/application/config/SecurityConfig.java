package pay.conflux.backend.application.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import pay.conflux.backend.application.security.ApiKeyAuthFilter;

/**
 * Wave B 8c security configuration: stateless filter chain in front of the merchant API surface.
 *
 * <p>The {@link ApiKeyAuthFilter} resolves the {@code Authorization: Bearer …} (or {@code
 * X-API-Key}) header against {@code provisioning.GetBusinessByApiKeyUseCase}, populates the
 * security context with a {@code MERCHANT} authority, and exposes the resolved {@code businessId}
 * as a request attribute. {@link EnableMethodSecurity} stays on so existing {@code @PreAuthorize}
 * annotations on controller implementations are honored.
 *
 * <p>{@code proxyTargetClass = true} forces CGLIB subclass proxies. JDK dynamic proxies would hide
 * the {@code @GetMapping}/{@code @PostMapping} annotations carried on the controller adapters,
 * leaving Spring MVC unable to discover any routes (resulting in 404s on every endpoint).
 */
@Configuration
@EnableMethodSecurity(proxyTargetClass = true)
@Profile("!openapi")
@RequiredArgsConstructor
public class SecurityConfig {

  private final ApiKeyAuthFilter apiKeyAuthFilter;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        new AntPathRequestMatcher("/api/v1/payments/callback/**"),
                        new AntPathRequestMatcher("/actuator/health"),
                        new AntPathRequestMatcher("/actuator/health/**"),
                        new AntPathRequestMatcher("/v3/api-docs"),
                        new AntPathRequestMatcher("/v3/api-docs/**"),
                        new AntPathRequestMatcher("/swagger-ui/**"),
                        new AntPathRequestMatcher("/swagger-ui.html"))
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
