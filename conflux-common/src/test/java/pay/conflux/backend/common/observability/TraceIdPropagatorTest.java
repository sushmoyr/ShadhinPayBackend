package pay.conflux.backend.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.UUID;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class TraceIdPropagatorTest {

  @AfterEach
  void clear() {
    MDC.clear();
  }

  @Test
  void okHttpInterceptor_addsHeaderFromMdc() throws IOException, InterruptedException {
    String id = UUID.randomUUID().toString();
    MDC.put(TraceIdFilter.MDC_KEY, id);

    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(200));
      server.start();

      OkHttpClient client =
          new OkHttpClient.Builder().addInterceptor(TraceIdPropagator.okHttpInterceptor()).build();
      try (Response response =
          client.newCall(new Request.Builder().url(server.url("/x")).get().build()).execute()) {
        assertThat(response.code()).isEqualTo(200);
      }

      RecordedRequest recorded = server.takeRequest();
      assertThat(recorded.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(id);
    }
  }

  @Test
  void okHttpInterceptor_skipsHeaderWhenMdcEmpty() throws IOException, InterruptedException {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(200));
      server.start();

      OkHttpClient client =
          new OkHttpClient.Builder().addInterceptor(TraceIdPropagator.okHttpInterceptor()).build();
      try (Response response =
          client.newCall(new Request.Builder().url(server.url("/x")).get().build()).execute()) {
        assertThat(response.code()).isEqualTo(200);
      }

      RecordedRequest recorded = server.takeRequest();
      assertThat(recorded.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isNull();
    }
  }

  @Test
  void clientHttpInterceptor_addsHeaderFromMdc() throws IOException {
    String id = UUID.randomUUID().toString();
    MDC.put(TraceIdFilter.MDC_KEY, id);

    ClientHttpRequestInterceptor interceptor = TraceIdPropagator.clientHttpInterceptor();
    MockClientHttpRequest request = new MockClientHttpRequest();
    interceptor.intercept(request, new byte[0], stubExecution());

    assertThat(request.getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(id);
  }

  @Test
  void clientHttpInterceptor_omitsHeaderWhenMdcEmpty() throws IOException {
    ClientHttpRequestInterceptor interceptor = TraceIdPropagator.clientHttpInterceptor();
    MockClientHttpRequest request = new MockClientHttpRequest();
    interceptor.intercept(request, new byte[0], stubExecution());

    assertThat(request.getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER)).isNull();
  }

  private static ClientHttpRequestExecution stubExecution() {
    return new ClientHttpRequestExecution() {
      @Override
      public ClientHttpResponse execute(HttpRequest req, byte[] body) {
        return new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK);
      }
    };
  }
}
