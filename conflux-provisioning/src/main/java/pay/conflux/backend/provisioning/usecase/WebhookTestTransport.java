package pay.conflux.backend.provisioning.usecase;

import java.io.IOException;
import java.util.Map;

/**
 * Outbound HTTP port for the test-webhook endpoint. Kept as a port (and not as a direct OkHttp call
 * inside {@code SendTestWebhookUseCaseImpl}) so the use-case test can drive every branch without
 * standing up an HTTP server. The single production implementation wraps a dedicated {@code
 * OkHttpClient} with a tight timeout budget.
 */
public interface WebhookTestTransport {

  /**
   * Send a signed JSON test payload to {@code url}. The implementation must not throw — transport
   * failures are returned as a {@link Result} with {@code statusCode == 0} and a populated {@code
   * error}.
   */
  Result post(String url, String jsonBody, Map<String, String> headers);

  /** Outcome of a single test-webhook delivery attempt. */
  record Result(int statusCode, long durationMs, String error) {

    public static Result success(int statusCode, long durationMs) {
      return new Result(statusCode, durationMs, null);
    }

    public static Result failure(int statusCode, long durationMs, IOException cause) {
      return new Result(statusCode, durationMs, cause.getClass().getSimpleName());
    }
  }
}
