package pay.conflux.backend.common.transport;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HttpsRedirectConfigTest {

  @Test
  void filterRegistration_isWiredAtHighestPrecedence() {
    FilterRegistrationBean<HttpsRedirectConfig.HstsFilter> bean =
        new HttpsRedirectConfig().hstsFilterRegistration();
    assertThat(bean.getFilter()).isInstanceOf(HttpsRedirectConfig.HstsFilter.class);
    assertThat(bean.getOrder()).isEqualTo(Integer.MIN_VALUE);
    assertThat(bean.getUrlPatterns()).contains("/*");
  }

  @Test
  void filter_emitsHstsHeader() throws Exception {
    HttpsRedirectConfig.HstsFilter filter = new HttpsRedirectConfig.HstsFilter();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/anything");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {};

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(HttpsRedirectConfig.HSTS_HEADER))
        .isEqualTo("max-age=31536000; includeSubDomains");
  }
}
