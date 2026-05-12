package pay.conflux.backend.paymentcore.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.crypto.HmacSigner;
import pay.conflux.backend.common.webhook.WebhookSigner;
import pay.conflux.backend.paymentcore.entity.WebhookEventType;
import pay.conflux.backend.paymentcore.entity.WebhookOutbox;
import pay.conflux.backend.paymentcore.entity.WebhookOutboxStatus;
import pay.conflux.backend.paymentcore.repository.WebhookOutboxRepository;
import pay.conflux.backend.provisioning.usecase.BusinessWebhookConfigDescriptor;
import pay.conflux.backend.provisioning.usecase.GetBusinessWebhookConfigUseCase;

@ExtendWith(MockitoExtension.class)
class HandleWebhookRetryUseCaseImplTest {

  @Mock private WebhookOutboxRepository webhookOutboxRepository;
  @Mock private GetBusinessWebhookConfigUseCase getBusinessWebhookConfigUseCase;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WebhookSigner webhookSigner = new WebhookSigner(new HmacSigner());
  private final OkHttpClient httpClient =
      new OkHttpClient.Builder()
          .connectTimeout(Duration.ofSeconds(2))
          .readTimeout(Duration.ofSeconds(2))
          .build();

  private MockWebServer server;
  private HandleWebhookRetryUseCaseImpl useCase;
  private UUID outboxId;
  private UUID businessId;
  private UUID transactionId;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    useCase =
        new HandleWebhookRetryUseCaseImpl(
            webhookOutboxRepository,
            getBusinessWebhookConfigUseCase,
            webhookSigner,
            httpClient,
            objectMapper);
    outboxId = UUID.randomUUID();
    businessId = UUID.randomUUID();
    transactionId = UUID.randomUUID();
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  private WebhookOutbox row(int attempt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("transactionId", transactionId.toString());
    payload.put("status", "COMPLETED");
    return WebhookOutbox.builder()
        .id(outboxId)
        .businessId(businessId)
        .transactionId(transactionId)
        .eventType(WebhookEventType.PAYMENT_COMPLETED)
        .payload(payload)
        .status(WebhookOutboxStatus.PENDING)
        .attemptCount(attempt)
        .nextAttemptAt(Instant.now())
        .build();
  }

  @Test
  void execute_2xx_marksSentAndStampsCount() throws Exception {
    WebhookOutbox r = row(0);
    when(webhookOutboxRepository.findById(outboxId)).thenReturn(Optional.of(r));
    when(getBusinessWebhookConfigUseCase.execute(businessId))
        .thenReturn(new BusinessWebhookConfigDescriptor(server.url("/hook").toString(), "secret"));
    server.enqueue(new MockResponse().setResponseCode(200));

    useCase.execute(outboxId);

    RecordedRequest captured = server.takeRequest();
    assertThat(captured.getMethod()).isEqualTo("POST");
    String sig = captured.getHeader(WebhookSigner.SIGNATURE_HEADER);
    assertThat(sig).isNotBlank();
    assertThat(webhookSigner.verify(captured.getBody().readUtf8(), "secret", sig)).isTrue();
    assertThat(r.getStatus()).isEqualTo(WebhookOutboxStatus.SENT);
    assertThat(r.getAttemptCount()).isEqualTo(1);
    assertThat(r.getLastError()).isNull();
    verify(webhookOutboxRepository).save(r);
  }

  @Test
  void execute_5xx_incrementsAttemptAndAdvancesBackoff() {
    WebhookOutbox r = row(0);
    when(webhookOutboxRepository.findById(outboxId)).thenReturn(Optional.of(r));
    when(getBusinessWebhookConfigUseCase.execute(businessId))
        .thenReturn(new BusinessWebhookConfigDescriptor(server.url("/hook").toString(), "secret"));
    server.enqueue(new MockResponse().setResponseCode(500));

    Instant before = Instant.now();
    useCase.execute(outboxId);

    assertThat(r.getStatus()).isEqualTo(WebhookOutboxStatus.PENDING);
    assertThat(r.getAttemptCount()).isEqualTo(1);
    assertThat(r.getLastError()).contains("HTTP 500");
    assertThat(r.getNextAttemptAt())
        .isAfterOrEqualTo(before.plus(Duration.ofSeconds(55)))
        .isBeforeOrEqualTo(before.plus(Duration.ofMinutes(2)));
  }

  @Test
  void execute_afterMaxAttempts_marksFailed() {
    WebhookOutbox r = row(5); // already attempted 5, next failure should be #6
    when(webhookOutboxRepository.findById(outboxId)).thenReturn(Optional.of(r));
    when(getBusinessWebhookConfigUseCase.execute(businessId))
        .thenReturn(new BusinessWebhookConfigDescriptor(server.url("/hook").toString(), "secret"));
    server.enqueue(new MockResponse().setResponseCode(500));

    useCase.execute(outboxId);

    assertThat(r.getStatus()).isEqualTo(WebhookOutboxStatus.FAILED);
    assertThat(r.getAttemptCount()).isEqualTo(6);
  }

  @Test
  void execute_hmacSignatureDeterministic() throws Exception {
    WebhookOutbox r1 = row(0);
    WebhookOutbox r2 = row(0);
    when(webhookOutboxRepository.findById(outboxId)).thenReturn(Optional.of(r1), Optional.of(r2));
    when(getBusinessWebhookConfigUseCase.execute(businessId))
        .thenReturn(new BusinessWebhookConfigDescriptor(server.url("/hook").toString(), "secret"));
    server.enqueue(new MockResponse().setResponseCode(200));
    server.enqueue(new MockResponse().setResponseCode(200));

    useCase.execute(outboxId);
    useCase.execute(outboxId);

    RecordedRequest first = server.takeRequest();
    RecordedRequest second = server.takeRequest();
    assertThat(first.getHeader(WebhookSigner.SIGNATURE_HEADER))
        .isEqualTo(second.getHeader(WebhookSigner.SIGNATURE_HEADER));
  }

  @Test
  void execute_skipsRowWhenBusinessHasNoWebhookConfigured() {
    WebhookOutbox r = row(0);
    when(webhookOutboxRepository.findById(outboxId)).thenReturn(Optional.of(r));
    when(getBusinessWebhookConfigUseCase.execute(businessId))
        .thenReturn(BusinessWebhookConfigDescriptor.empty());

    useCase.execute(outboxId);

    assertThat(r.getStatus()).isEqualTo(WebhookOutboxStatus.SENT);
    assertThat(r.getLastError()).contains("no webhook url configured");
  }

  @Test
  void execute_noLogContainsSecret(org.junit.jupiter.api.TestInfo info) throws Exception {
    // Hardened regression: verify no payload/secret ever lands on the saved row's lastError.
    WebhookOutbox r = row(0);
    when(webhookOutboxRepository.findById(outboxId)).thenReturn(Optional.of(r));
    when(getBusinessWebhookConfigUseCase.execute(businessId))
        .thenReturn(
            new BusinessWebhookConfigDescriptor(
                server.url("/hook").toString(), "super-secret-token-xyz"));
    server.enqueue(new MockResponse().setResponseCode(500));

    useCase.execute(outboxId);

    ArgumentCaptor<WebhookOutbox> captor = ArgumentCaptor.forClass(WebhookOutbox.class);
    verify(webhookOutboxRepository, atLeastOnce()).save(captor.capture());
    for (WebhookOutbox saved : captor.getAllValues()) {
      if (saved.getLastError() != null) {
        assertThat(saved.getLastError()).doesNotContain("super-secret-token-xyz");
      }
    }
  }

  @Test
  void execute_unknownRow_noOps() {
    when(webhookOutboxRepository.findById(outboxId)).thenReturn(Optional.empty());
    useCase.execute(outboxId);
    verify(webhookOutboxRepository, times(0)).save(any(WebhookOutbox.class));
  }
}
