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
import org.springframework.security.core.context.SecurityContextHolder;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.provisioning.usecase.BusinessContext;
import pay.conflux.backend.provisioning.usecase.GetBusinessByApiKeyUseCase;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

  @Mock private GetBusinessByApiKeyUseCase getBusinessByApiKeyUseCase;
  @Mock private FilterChain chain;

  private ApiKeyAuthFilter filter;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @BeforeEach
  void setUp() {
    filter = new ApiKeyAuthFilter(getBusinessByApiKeyUseCase, objectMapper);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldNotFilter_vendorCallbackPath_isWhitelisted() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRequestURI("/api/v1/payments/callback/MOCK");
    assertThat(filter.shouldNotFilter(req)).isTrue();
  }

  @Test
  void shouldNotFilter_actuatorHealth_isWhitelisted() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRequestURI("/actuator/health");
    assertThat(filter.shouldNotFilter(req)).isTrue();
  }

  @Test
  void shouldNotFilter_apiDocs_isWhitelisted() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRequestURI("/v3/api-docs/something");
    assertThat(filter.shouldNotFilter(req)).isTrue();
  }

  @Test
  void shouldNotFilter_protectedRoute_returnsFalse() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRequestURI("/api/v1/payments");
    assertThat(filter.shouldNotFilter(req)).isFalse();
  }

  @Test
  void doFilter_validBearerKey_populatesContextAndAttribute() throws Exception {
    UUID businessId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    when(getBusinessByApiKeyUseCase.execute("sp_live_abc"))
        .thenReturn(
            new BusinessContext(
                businessId, merchantId, "LIVE", "https://merchant.example/webhook"));

    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/payments");
    req.addHeader("Authorization", "Bearer sp_live_abc");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(req.getAttribute("X-Business-Id")).isEqualTo(businessId);
    assertThat(req.getAttribute("X-Environment")).isEqualTo("LIVE");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isEqualTo(businessId);
    verify(chain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
  }

  @Test
  void doFilter_xApiKeyHeader_isAccepted() throws Exception {
    UUID businessId = UUID.randomUUID();
    when(getBusinessByApiKeyUseCase.execute("sp_test_xyz"))
        .thenReturn(new BusinessContext(businessId, UUID.randomUUID(), "TEST", ""));

    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/payments");
    req.addHeader("X-API-Key", "sp_test_xyz");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(req.getAttribute("X-Business-Id")).isEqualTo(businessId);
    verify(chain).doFilter(any(), any());
  }

  @Test
  void doFilter_missingKey_returns401() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/payments");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(res.getContentAsString()).contains("Invalid API key");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void doFilter_invalidKey_returns401WithoutLeakingKey() throws Exception {
    when(getBusinessByApiKeyUseCase.execute("sp_live_revoked"))
        .thenThrow(new UnauthorizedException("Invalid API key"));

    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/payments");
    req.addHeader("Authorization", "Bearer sp_live_revoked");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(res.getContentAsString()).doesNotContain("sp_live_revoked");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void doFilter_blankBearerToken_treatedAsMissing() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/payments");
    req.addHeader("Authorization", "Bearer ");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    verify(chain, never()).doFilter(any(), any());
  }
}
