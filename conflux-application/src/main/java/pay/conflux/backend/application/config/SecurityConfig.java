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
import pay.conflux.backend.application.security.JwtAuthorizationFilter;

/**
 * Stateless filter chain in front of every authenticated surface.
 *
 * <p>Two coexisting auth filters run in order:
 *
 * <ol>
 *   <li>{@link JwtAuthorizationFilter} — claims requests whose {@code Authorization: Bearer …}
 *       header is a JWT (three Base64URL segments separated by dots). Populates an {@code
 *       AuthenticatedPrincipal} with tier-aware authorities resolved by {@code
 *       AdminAuthorityResolver}.
 *   <li>{@link ApiKeyAuthFilter} — claims requests carrying an opaque API key in either the
 *       Authorization bearer position or {@code X-API-Key}. Populates the context with a {@code
 *       MERCHANT} authority and exposes the resolved {@code businessId} as a request attribute.
 * </ol>
 *
 * <p>JWT runs first so that a JWT-shaped value is never reinterpreted as an opaque API key; a
 * malformed JWT 401s rather than falling through. Both filters short-circuit when the context is
 * already authenticated, so they never double-auth a single request.
 *
 * <p>{@link EnableMethodSecurity} stays on so existing {@code @PreAuthorize} annotations on
 * controller implementations are honored. {@code proxyTargetClass = true} forces CGLIB subclass
 * proxies — JDK dynamic proxies would hide the {@code @GetMapping}/{@code @PostMapping} annotations
 * carried on the controller adapters, leaving Spring MVC unable to discover any routes (resulting
 * in 404s on every endpoint).
 */
@Configuration
@EnableMethodSecurity(proxyTargetClass = true)
@Profile("!openapi")
@RequiredArgsConstructor
public class SecurityConfig {

  private final ApiKeyAuthFilter apiKeyAuthFilter;
  private final JwtAuthorizationFilter jwtAuthorizationFilter;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        new AntPathRequestMatcher("/api/v1/payments/callback/**"),
                        new AntPathRequestMatcher("/api/v1/auth/login"),
                        new AntPathRequestMatcher("/api/v1/merchant/register"),
                        new AntPathRequestMatcher("/actuator/health"),
                        new AntPathRequestMatcher("/actuator/health/**"),
                        new AntPathRequestMatcher("/v3/api-docs"),
                        new AntPathRequestMatcher("/v3/api-docs/**"),
                        new AntPathRequestMatcher("/swagger-ui/**"),
                        new AntPathRequestMatcher("/swagger-ui.html"))
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthorizationFilter, ApiKeyAuthFilter.class);
    return http.build();
  }
}
