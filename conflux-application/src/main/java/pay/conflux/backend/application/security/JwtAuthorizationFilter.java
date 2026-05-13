package pay.conflux.backend.application.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.common.error.ErrorCode;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.common.security.AuthenticatedPrincipal;
import pay.conflux.backend.identity.usecase.ResolveJwtPrincipalUseCase;

/**
 * Resolves a {@code Authorization: Bearer <jwt>} header against the identity module's {@link
 * ResolveJwtPrincipalUseCase} and populates Spring Security with an {@link AuthenticatedPrincipal}
 * carrying the tier-aware authority list from {@link AdminAuthorityResolver}.
 *
 * <p>Ordered <em>before</em> {@link ApiKeyAuthFilter} in the chain: JWT-shaped tokens are claimed
 * here, opaque API-key strings fall through. A JWT-shaped value that fails verification produces a
 * 401 — it must never silently fall through to API-key auth, because the caller clearly intended
 * JWT authentication and an opaque-key reinterpretation would be a security regression.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthorizationFilter extends OncePerRequestFilter {

  static final String AUTH_HEADER = "Authorization";
  static final String BEARER_PREFIX = "Bearer ";

  /**
   * JWT compact form: three Base64URL segments separated by dots. Distinguishes JWTs from opaque
   * API keys at the filter boundary so the latter fall through to {@link ApiKeyAuthFilter}.
   */
  private static final Pattern JWT_PATTERN =
      Pattern.compile("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");

  // TODO(post-1c): consolidate this whitelist with ApiKeyAuthFilter's into a shared
  //                SecurityWhitelist constant. Duplicated for now per 1c's "do not edit
  //                ApiKeyAuthFilter" constraint.
  private static final List<String> WHITELIST =
      List.of(
          "/api/v1/payments/callback/**",
          "/api/v1/auth/login",
          "/api/v1/merchant/register",
          "/actuator/**",
          "/v3/api-docs",
          "/v3/api-docs/**",
          "/swagger-ui/**",
          "/swagger-ui.html");

  private static final AntPathMatcher MATCHER = new AntPathMatcher();

  private final ResolveJwtPrincipalUseCase resolveJwtPrincipalUseCase;
  private final ObjectMapper objectMapper;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    for (String pattern : WHITELIST) {
      if (MATCHER.match(pattern, path)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    Authentication existing = SecurityContextHolder.getContext().getAuthentication();
    if (existing != null && existing.isAuthenticated()) {
      chain.doFilter(request, response);
      return;
    }

    String bearerValue = extractBearer(request);
    if (bearerValue == null) {
      chain.doFilter(request, response);
      return;
    }

    if (!JWT_PATTERN.matcher(bearerValue).matches()) {
      // Opaque API-key shape — defer to ApiKeyAuthFilter rather than 401 here.
      chain.doFilter(request, response);
      return;
    }

    ResolveJwtPrincipalUseCase.Resolved resolved;
    try {
      resolved = resolveJwtPrincipalUseCase.execute(bearerValue);
    } catch (UnauthorizedException ex) {
      log.warn("JWT rejected: {}", ex.getMessage());
      writeUnauthorized(response, ex.getMessage());
      return;
    }

    // Merchants reuse user.id as merchantId — see MerchantVerifiedEventListener wiring where the
    // event's userId becomes the Business.merchantId. Admins are environment-agnostic, so the
    // remaining principal fields stay null per spec.
    UUID merchantId =
        resolved.userType() == AuthenticatedPrincipal.UserType.MERCHANT ? resolved.userId() : null;
    AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal(resolved.userId(), resolved.userType(), merchantId, null, null);
    List<GrantedAuthority> authorities =
        AdminAuthorityResolver.resolve(resolved.userType(), resolved.adminTierName());

    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    chain.doFilter(request, response);
  }

  private static String extractBearer(HttpServletRequest request) {
    String header = request.getHeader(AUTH_HEADER);
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      return null;
    }
    String value = header.substring(BEARER_PREFIX.length()).trim();
    return value.isEmpty() ? null : value;
  }

  private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getWriter(),
        ApiResult.error(HttpStatus.UNAUTHORIZED, message, ErrorCode.UNAUTHORIZED).getBody());
  }
}
