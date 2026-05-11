package pay.conflux.backend.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

  private final TraceIdFilter filter = new TraceIdFilter();

  @AfterEach
  void clear() {
    MDC.clear();
  }

  @Test
  void missingHeader_generatesUuidAndEchoesOnResponse() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> mdcDuringRequest = new AtomicReference<>();
    FilterChain chain = (req, res) -> mdcDuringRequest.set(MDC.get(TraceIdFilter.MDC_KEY));

    filter.doFilter(request, response, chain);

    String header = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
    assertThat(header).isNotNull();
    assertThat(UUID.fromString(header)).isNotNull();
    assertThat(mdcDuringRequest.get()).isEqualTo(header);
    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void validHeader_isEchoedUnchanged() throws Exception {
    String existing = UUID.randomUUID().toString();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
    request.addHeader(TraceIdFilter.TRACE_ID_HEADER, existing);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> mdcDuringRequest = new AtomicReference<>();
    FilterChain chain = (req, res) -> mdcDuringRequest.set(MDC.get(TraceIdFilter.MDC_KEY));

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(existing);
    assertThat(mdcDuringRequest.get()).isEqualTo(existing);
  }

  @Test
  void malformedHeader_isReplacedWithFreshUuid() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
    request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "not-a-uuid");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> mdcDuringRequest = new AtomicReference<>();
    FilterChain chain = (req, res) -> mdcDuringRequest.set(MDC.get(TraceIdFilter.MDC_KEY));

    filter.doFilter(request, response, chain);

    String written = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
    assertThat(written).isNotEqualTo("not-a-uuid");
    assertThat(UUID.fromString(written)).isNotNull();
    assertThat(mdcDuringRequest.get()).isEqualTo(written);
  }

  @Test
  void mdcIsClearedEvenWhenChainThrows() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain =
        (req, res) -> {
          throw new RuntimeException("boom");
        };

    try {
      filter.doFilter(request, response, chain);
    } catch (RuntimeException expected) {
      // intentional
    }

    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isNotNull();
  }

  @Test
  void blankHeaderIsTreatedAsMissing() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
    request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "   ");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {};

    filter.doFilter(request, response, chain);

    String written = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
    assertThat(UUID.fromString(written)).isNotNull();
  }
}
