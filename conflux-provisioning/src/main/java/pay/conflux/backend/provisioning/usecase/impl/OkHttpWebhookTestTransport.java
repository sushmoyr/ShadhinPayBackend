package pay.conflux.backend.provisioning.usecase.impl;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import pay.conflux.backend.provisioning.usecase.WebhookTestTransport;

/**
 * Synchronous OkHttp adapter for the test-webhook endpoint. The 5 s connect / 5 s read budget keeps
 * a slow merchant URL from tying up a Tomcat thread — failures are returned as a Result, never
 * thrown, so the controller can render an actionable response.
 */
@Slf4j
@Component
public class OkHttpWebhookTestTransport implements WebhookTestTransport {

  private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

  private final OkHttpClient httpClient;

  public OkHttpWebhookTestTransport() {
    this.httpClient =
        new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(5))
            .writeTimeout(Duration.ofSeconds(5))
            .callTimeout(Duration.ofSeconds(10))
            .retryOnConnectionFailure(false)
            .build();
  }

  @Override
  public Result post(String url, String jsonBody, Map<String, String> headers) {
    Request.Builder builder =
        new Request.Builder().url(url).post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE));
    headers.forEach(builder::header);

    long start = System.currentTimeMillis();
    int statusCode = 0;
    try (Response response = httpClient.newCall(builder.build()).execute()) {
      statusCode = response.code();
      return Result.success(statusCode, System.currentTimeMillis() - start);
    } catch (IOException e) {
      return Result.failure(statusCode, System.currentTimeMillis() - start, e);
    }
  }
}
