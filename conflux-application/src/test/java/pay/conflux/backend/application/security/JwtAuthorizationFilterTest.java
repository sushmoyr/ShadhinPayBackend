package pay.conflux.backend.application.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.common.security.AuthenticatedPrincipal;
import pay.conflux.backend.identity.usecase.ResolveJwtPrincipalUseCase;

@ExtendWith(MockitoExtension.class)
class JwtAuthorizationFilterTest {

  // A real-looking JWT-shape string (three Base64URL segments). The token is opaque from the
  // filter's perspective — ResolveJwtPrincipalUseCase is mocked to return the desired Resolved.
  private static final String JWT_SHAPED = "aaaa.bbbb.cccc";

  @Mock private ResolveJwtPrincipalUseCase resolveJwtPrincipalUseCase;
  @Mock private FilterChain chain;

  private JwtAuthorizationFilter filter;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @BeforeEach
  void setUp() {
    filter = new JwtAuthorizationFilter(resolveJwtPrincipalUseCase, objectMapper);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_noAuthorizationHeader_passesThroughAndContextUnset() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin/me");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
  }

  @Test
  void doFilter_xApiKeyHeaderOnly_passesThroughWithoutParsing() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/payments");
    req.addHeader("X-API-Key", "sp_live_opaque_string");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(any(), any());
    verify(resolveJwtPrincipalUseCase, never()).execute(any());
  }

  @Test
  void doFilter_bearerOpaqueKeyShape_passesThroughToApiKeyFilter() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/payments");
    req.addHeader("Authorization", "Bearer sp_live_no_dots_in_here");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(any(), any());
    verify(resolveJwtPrincipalUseCase, never()).execute(any());
  }

  @Test
  void doFilter_validMerchantJwt_setsMerchantAuthorityAndMerchantId() throws Exception {
    UUID userId = UUID.randomUUID();
    when(resolveJwtPrincipalUseCase.execute(JWT_SHAPED))
        .thenReturn(
            new ResolveJwtPrincipalUseCase.Resolved(
                userId, AuthenticatedPrincipal.UserType.MERCHANT, null));

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/business");
    req.addHeader("Authorization", "Bearer " + JWT_SHAPED);
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, chain);

    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    AuthenticatedPrincipal principal = (AuthenticatedPrincipal) auth.getPrincipal();
    assertThat(principal.userId()).isEqualTo(userId);
    assertThat(principal.userType()).isEqualTo(AuthenticatedPrincipal.UserType.MERCHANT);
    assertThat(principal.merchantId())
        .as("merchantId mirrors userId so SecurityUtils.currentMerchantId() resolves")
        .isEqualTo(userId);
    assertThat(auth.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("MERCHANT");
    verify(chain).doFilter(any(), any());
  }

  @Test
  void doFilter_validSuperAdminJwt_setsAllThreeAdminAuthorities() throws Exception {
    UUID userId = UUID.randomUUID();
    when(resolveJwtPrincipalUseCase.execute(JWT_SHAPED))
        .thenReturn(
            new ResolveJwtPrincipalUseCase.Resolved(
                userId, AuthenticatedPrincipal.UserType.ADMIN, "SUPER"));

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin/admins");
    req.addHeader("Authorization", "Bearer " + JWT_SHAPED);
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, chain);

    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    AuthenticatedPrincipal principal = (AuthenticatedPrincipal) auth.getPrincipal();
    assertThat(principal.userType()).isEqualTo(AuthenticatedPrincipal.UserType.ADMIN);
    assertThat(principal.merchantId()).as("admins are not merchants").isNull();
    assertThat(auth.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ADMIN_VIEWER", "ADMIN_MANAGER", "SUPER_ADMIN");
  }

  @Test
  void doFilter_expiredJwt_returns401AndContextUnset() throws Exception {
    when(resolveJwtPrincipalUseCase.execute(JWT_SHAPED))
        .thenThrow(new UnauthorizedException("JWT is expired"));

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin/me");
    req.addHeader("Authorization", "Bearer " + JWT_SHAPED);
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(res.getContentAsString()).contains("expired");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void doFilter_tamperedJwt_returns401WithApiResultEnvelope() throws Exception {
    when(resolveJwtPrincipalUseCase.execute(JWT_SHAPED))
        .thenThrow(new UnauthorizedException("JWT is invalid"));

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin/me");
    req.addHeader("Authorization", "Bearer " + JWT_SHAPED);
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(res.getContentType()).startsWith("application/json");
    assertThat(res.getContentAsString()).contains("\"errorCode\":\"UNAUTHORIZED\"");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void doFilter_blockedUser_returns401() throws Exception {
    when(resolveJwtPrincipalUseCase.execute(JWT_SHAPED))
        .thenThrow(new UnauthorizedException("User is not active"));

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/business");
    req.addHeader("Authorization", "Bearer " + JWT_SHAPED);
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(res.getContentAsString()).contains("not active");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void doFilter_alreadyAuthenticated_passesThroughWithoutReplacingContext() throws Exception {
    UUID prior = UUID.randomUUID();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                prior, null, java.util.List.of()));

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/admin/me");
    req.addHeader("Authorization", "Bearer " + JWT_SHAPED);
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isEqualTo(prior);
    verify(chain).doFilter(any(), any());
    verify(resolveJwtPrincipalUseCase, never()).execute(any());
  }

  @Test
  void shouldNotFilter_loginEndpoint_isWhitelisted() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRequestURI("/api/v1/auth/login");
    assertThat(filter.shouldNotFilter(req)).isTrue();
  }

  @Test
  void shouldNotFilter_adminAdminsRoute_isNotWhitelisted() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRequestURI("/api/v1/admin/admins");
    assertThat(filter.shouldNotFilter(req)).isFalse();
  }
}
