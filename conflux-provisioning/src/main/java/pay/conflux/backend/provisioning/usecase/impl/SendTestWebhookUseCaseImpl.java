package pay.conflux.backend.provisioning.usecase.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.common.webhook.WebhookSigner;
import pay.conflux.backend.provisioning.dto.TestWebhookResultDto;
import pay.conflux.backend.provisioning.usecase.BusinessWebhookConfigDescriptor;
import pay.conflux.backend.provisioning.usecase.GetBusinessWebhookConfigUseCase;
import pay.conflux.backend.provisioning.usecase.SendTestWebhookUseCase;
import pay.conflux.backend.provisioning.usecase.WebhookTestTransport;

/**
 * Builds a deterministic {@code webhook.test} payload, signs it with the business's stored secret,
 * and dispatches it via {@link WebhookTestTransport}. The plaintext signature and the secret are
 * never logged — only the delivery outcome ({@code statusCode}, {@code durationMs}, error class)
 * surfaces.
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class SendTestWebhookUseCaseImpl implements SendTestWebhookUseCase {

  static final String EVENT_TYPE = "webhook.test";

  private final GetBusinessWebhookConfigUseCase getBusinessWebhookConfigUseCase;
  private final WebhookSigner webhookSigner;
  private final WebhookTestTransport transport;
  private final ObjectMapper objectMapper;

  @Override
  public TestWebhookResultDto execute(UUID businessId) {
    BusinessWebhookConfigDescriptor descriptor =
        getBusinessWebhookConfigUseCase.execute(businessId);
    if (!descriptor.isConfigured()) {
      throw new ValidationException("Webhook URL is not configured for this business");
    }

    Instant attemptedAt = Instant.now();
    UUID traceId = UUID.randomUUID();
    String body = serialize(businessId, attemptedAt, traceId);
    String signature = webhookSigner.signatureFor(body, descriptor.webhookSecret());

    Map<String, String> headers =
        Map.of(
            WebhookSigner.SIGNATURE_HEADER,
            signature,
            "X-PGW-Event",
            EVENT_TYPE,
            "X-PGW-Trace-ID",
            traceId.toString());

    WebhookTestTransport.Result result = transport.post(descriptor.webhookUrl(), body, headers);

    boolean delivered =
        result.error() == null && result.statusCode() >= 200 && result.statusCode() < 300;
    log.info(
        "Test webhook attempt [businessId={}, statusCode={}, durationMs={}, delivered={},"
            + " error={}]",
        businessId,
        result.statusCode(),
        result.durationMs(),
        delivered,
        result.error());

    return new TestWebhookResultDto(
        delivered, result.statusCode(), result.durationMs(), attemptedAt, result.error());
  }

  private String serialize(UUID businessId, Instant attemptedAt, UUID traceId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("event", EVENT_TYPE);
    payload.put("businessId", businessId.toString());
    payload.put("traceId", traceId.toString());
    payload.put("timestamp", attemptedAt.toString());
    payload.put("note", "This is a synthetic webhook from the merchant dashboard test button.");
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize test webhook payload", e);
    }
  }
}
