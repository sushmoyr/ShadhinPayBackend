package pay.conflux.backend.application.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.common.error.ErrorCode;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.provisioning.usecase.BusinessContext;
import pay.conflux.backend.provisioning.usecase.GetBusinessByApiKeyUseCase;

/**
 * Bridges the provisioning {@code GetBusinessByApiKeyUseCase} into Spring Security: every request
 * that carries an API key gets a populated {@link SecurityContextHolder} authentication plus a
 * request attribute exposing the resolved {@code businessId} for downstream controllers to read via
 * {@code @RequestAttribute}.
 *
 * <p>Whitelisted routes (vendor callback, actuator health, OpenAPI docs) skip authentication. All
 * other paths require either {@code Authorization: Bearer <key>} or {@code X-API-Key}; missing or
 * invalid keys produce a {@link HttpStatus#UNAUTHORIZED} response shaped as {@link
 * ApiResult#error}.
 *
 * <p>The plaintext key is forwarded directly to the resolver — it is hashed there with SHA-256
 * before the lookup. The filter logs only resolution outcomes (no key material, no header bodies).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

  public static final String ATTR_BUSINESS_ID = "X-Business-Id";
  public static final String ATTR_ENVIRONMENT = "X-Environment";
  public static final String AUTH_HEADER = "Authorization";
  public static final String API_KEY_HEADER = "X-API-Key";
  public static final String BEARER_PREFIX = "Bearer ";

  private static final List<String> WHITELIST =
      List.of(
          "/api/v1/payments/callback/**",
          "/actuator/health",
          "/actuator/health/**",
          "/v3/api-docs",
          "/v3/api-docs/**",
          "/swagger-ui/**",
          "/swagger-ui.html");

  private static final AntPathMatcher MATCHER = new AntPathMatcher();

  private final GetBusinessByApiKeyUseCase getBusinessByApiKeyUseCase;
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

    String apiKey = extractKey(request);
    if (apiKey == null) {
      writeUnauthorized(response, "Invalid API key");
      return;
    }

    try {
      BusinessContext ctx = getBusinessByApiKeyUseCase.execute(apiKey);
      request.setAttribute(ATTR_BUSINESS_ID, ctx.businessId());
      request.setAttribute(ATTR_ENVIRONMENT, ctx.environment());

      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(
              ctx.businessId(), null, List.of(new SimpleGrantedAuthority("MERCHANT")));
      SecurityContextHolder.getContext().setAuthentication(auth);
      chain.doFilter(request, response);
    } catch (UnauthorizedException ex) {
      log.warn("api-key resolution failed: {}", ex.getMessage());
      writeUnauthorized(response, "Invalid API key");
    }
  }

  private static String extractKey(HttpServletRequest request) {
    String header = request.getHeader(AUTH_HEADER);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      String value = header.substring(BEARER_PREFIX.length()).trim();
      if (!value.isEmpty()) {
        return value;
      }
    }
    String apiKeyHeader = request.getHeader(API_KEY_HEADER);
    if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
      return apiKeyHeader.trim();
    }
    return null;
  }

  private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getWriter(),
        ApiResult.error(HttpStatus.UNAUTHORIZED, message, ErrorCode.UNAUTHORIZED).getBody());
  }
}
