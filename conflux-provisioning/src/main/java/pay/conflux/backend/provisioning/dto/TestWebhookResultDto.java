package pay.conflux.backend.provisioning.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response shape for the synchronous test-webhook endpoint. {@code delivered} is {@code true} iff
 * the merchant URL returned a 2xx; on transport failures {@code statusCode} is {@code 0} and {@code
 * error} carries the failure class name ({@code SocketTimeoutException}, {@code ConnectException},
 * ...) — never a full stack trace, never a secret.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public class TestWebhookResultDto {
  private boolean delivered;
  private int statusCode;
  private long durationMs;
  private Instant attemptedAt;
  private String error;
}
