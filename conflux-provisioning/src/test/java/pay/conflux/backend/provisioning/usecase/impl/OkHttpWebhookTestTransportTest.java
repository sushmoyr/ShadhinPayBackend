package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.provisioning.usecase.WebhookTestTransport;

class OkHttpWebhookTestTransportTest {

  private MockWebServer server;
  private OkHttpWebhookTestTransport transport;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    transport = new OkHttpWebhookTestTransport();
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void post_returnsServerStatusAndForwardsHeaders() throws InterruptedException {
    server.enqueue(new MockResponse().setResponseCode(202).setBody("ok"));

    WebhookTestTransport.Result result =
        transport.post(
            server.url("/webhook").toString(),
            "{\"event\":\"webhook.test\"}",
            Map.of("X-PGW-Signature", "deadbeef", "X-PGW-Event", "webhook.test"));

    assertThat(result.statusCode()).isEqualTo(202);
    assertThat(result.error()).isNull();
    assertThat(result.durationMs()).isGreaterThanOrEqualTo(0L);

    RecordedRequest recorded = server.takeRequest();
    assertThat(recorded.getMethod()).isEqualTo("POST");
    assertThat(recorded.getHeader("X-PGW-Signature")).isEqualTo("deadbeef");
    assertThat(recorded.getHeader("Content-Type")).contains("application/json");
    assertThat(recorded.getBody().readUtf8()).isEqualTo("{\"event\":\"webhook.test\"}");
  }
}
