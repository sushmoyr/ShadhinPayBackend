package pay.conflux.backend.paymentcore.usecase.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.webhook.WebhookSigner;
import pay.conflux.backend.paymentcore.entity.WebhookOutbox;
import pay.conflux.backend.paymentcore.entity.WebhookOutboxStatus;
import pay.conflux.backend.paymentcore.repository.WebhookOutboxRepository;
import pay.conflux.backend.paymentcore.usecase.HandleWebhookRetryUseCase;
import pay.conflux.backend.provisioning.usecase.BusinessWebhookConfigDescriptor;
import pay.conflux.backend.provisioning.usecase.GetBusinessWebhookConfigUseCase;

/**
 * Executes a single webhook delivery attempt. Failures are persisted as backoff state on the row,
 * never thrown to the caller — the scheduled poller relies on this to keep draining.
 *
 * <p>The HMAC-SHA256 signature is computed inside this method, but neither the secret nor the
 * signed payload is logged. Log lines carry only {@code (businessId, transactionId, eventType,
 * statusCode, attemptCount)}.
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class HandleWebhookRetryUseCaseImpl implements HandleWebhookRetryUseCase {

  static final List<Duration> BACKOFF_SCHEDULE =
      List.of(
          Duration.ofMinutes(1),
          Duration.ofMinutes(5),
          Duration.ofMinutes(15),
          Duration.ofHours(1),
          Duration.ofHours(6),
          Duration.ofHours(24));
  static final int MAX_ATTEMPTS = BACKOFF_SCHEDULE.size();
  private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

  private final WebhookOutboxRepository webhookOutboxRepository;
  private final GetBusinessWebhookConfigUseCase getBusinessWebhookConfigUseCase;
  private final WebhookSigner webhookSigner;

  @Qualifier("webhookHttpClient")
  private final OkHttpClient httpClient;

  private final ObjectMapper objectMapper;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void execute(UUID outboxId) {
    WebhookOutbox row = webhookOutboxRepository.findById(outboxId).orElse(null);
    if (row == null) {
      log.warn("Webhook outbox row vanished mid-dispatch [outboxId={}]", outboxId);
      return;
    }
    if (row.getStatus() != WebhookOutboxStatus.PENDING) {
      return;
    }

    BusinessWebhookConfigDescriptor descriptor;
    try {
      descriptor = getBusinessWebhookConfigUseCase.execute(row.getBusinessId());
    } catch (RuntimeException e) {
      recordFailure(row, 0, "webhook config lookup failed");
      log.warn(
          "Webhook config lookup failed [businessId={}, transactionId={}, eventType={},"
              + " attemptCount={}]",
          row.getBusinessId(),
          row.getTransactionId(),
          row.getEventType(),
          row.getAttemptCount(),
          e);
      return;
    }
    if (!descriptor.isConfigured()) {
      // No webhook configured — drop the row as SENT so the dispatcher stops re-polling it.
      row.setStatus(WebhookOutboxStatus.SENT);
      row.setLastError("no webhook url configured for business");
      webhookOutboxRepository.save(row);
      return;
    }

    String body = serializePayload(row);
    if (body == null) {
      recordFailure(row, 0, "payload serialization failed");
      return;
    }
    String signature = webhookSigner.signatureFor(body, descriptor.webhookSecret());

    Request request =
        new Request.Builder()
            .url(descriptor.webhookUrl())
            .post(RequestBody.create(body, JSON_MEDIA_TYPE))
            .header(WebhookSigner.SIGNATURE_HEADER, signature)
            .header("X-PGW-Event", row.getEventType().name())
            .header("X-PGW-Transaction-Id", row.getTransactionId().toString())
            .build();

    int statusCode = 0;
    try (Response response = httpClient.newCall(request).execute()) {
      statusCode = response.code();
      if (response.isSuccessful()) {
        row.setStatus(WebhookOutboxStatus.SENT);
        row.setLastError(null);
        row.setAttemptCount(row.getAttemptCount() + 1);
        webhookOutboxRepository.save(row);
        log.info(
            "Webhook delivered [businessId={}, transactionId={}, eventType={}, statusCode={},"
                + " attemptCount={}]",
            row.getBusinessId(),
            row.getTransactionId(),
            row.getEventType(),
            statusCode,
            row.getAttemptCount());
        return;
      }
      recordFailure(row, statusCode, "non-2xx response: " + statusCode);
    } catch (IOException e) {
      recordFailure(row, statusCode, "transport error: " + e.getClass().getSimpleName());
    } catch (RuntimeException e) {
      recordFailure(row, statusCode, "dispatch threw: " + e.getClass().getSimpleName());
    }
    log.info(
        "Webhook delivery failed [businessId={}, transactionId={}, eventType={}, statusCode={},"
            + " attemptCount={}]",
        row.getBusinessId(),
        row.getTransactionId(),
        row.getEventType(),
        statusCode,
        row.getAttemptCount());
  }

  private void recordFailure(WebhookOutbox row, int statusCode, String error) {
    int newAttempt = row.getAttemptCount() + 1;
    row.setAttemptCount(newAttempt);
    row.setLastError(statusCode > 0 ? error + " (HTTP " + statusCode + ")" : error);
    if (newAttempt >= MAX_ATTEMPTS) {
      row.setStatus(WebhookOutboxStatus.FAILED);
      row.setNextAttemptAt(Instant.now());
    } else {
      Duration backoff = BACKOFF_SCHEDULE.get(newAttempt - 1);
      row.setNextAttemptAt(Instant.now().plus(backoff));
    }
    webhookOutboxRepository.save(row);
  }

  private String serializePayload(WebhookOutbox row) {
    try {
      return objectMapper.writeValueAsString(row.getPayload());
    } catch (JsonProcessingException e) {
      log.warn(
          "Webhook payload serialization failed [outboxId={}, transactionId={}]",
          row.getId(),
          row.getTransactionId(),
          e);
      return null;
    }
  }
}
