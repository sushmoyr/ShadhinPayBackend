package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.common.webhook.WebhookSigner;
import pay.conflux.backend.provisioning.dto.TestWebhookResultDto;
import pay.conflux.backend.provisioning.usecase.BusinessWebhookConfigDescriptor;
import pay.conflux.backend.provisioning.usecase.GetBusinessWebhookConfigUseCase;
import pay.conflux.backend.provisioning.usecase.WebhookTestTransport;

@ExtendWith(MockitoExtension.class)
class SendTestWebhookUseCaseImplTest {

  @Mock private GetBusinessWebhookConfigUseCase getBusinessWebhookConfigUseCase;
  @Mock private WebhookSigner webhookSigner;
  @Mock private WebhookTestTransport transport;

  private SendTestWebhookUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new SendTestWebhookUseCaseImpl(
            getBusinessWebhookConfigUseCase, webhookSigner, transport, new ObjectMapper());
  }

  @Test
  void execute_throwsValidationWhenNoWebhookConfigured() {
    UUID businessId = UUID.randomUUID();
    when(getBusinessWebhookConfigUseCase.execute(businessId))
        .thenReturn(BusinessWebhookConfigDescriptor.empty());

    assertThatThrownBy(() -> useCase.execute(businessId))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Webhook URL is not configured");
  }

  @Test
  void execute_signsPayloadAndReportsSuccessOn2xx() {
    UUID businessId = UUID.randomUUID();
    when(getBusinessWebhookConfigUseCase.execute(businessId))
        .thenReturn(
            new BusinessWebhookConfigDescriptor("https://merchant.example/webhook", "s3cr3t"));
    when(webhookSigner.signatureFor(anyString(), eq("s3cr3t"))).thenReturn("deadbeef");
    when(transport.post(eq("https://merchant.example/webhook"), anyString(), any()))
        .thenReturn(WebhookTestTransport.Result.success(200, 87L));

    TestWebhookResultDto result = useCase.execute(businessId);

    assertThat(result.isDelivered()).isTrue();
    assertThat(result.getStatusCode()).isEqualTo(200);
    assertThat(result.getDurationMs()).isEqualTo(87L);
    assertThat(result.getError()).isNull();
    assertThat(result.getAttemptedAt()).isNotNull();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(transport)
        .post(eq("https://merchant.example/webhook"), body.capture(), headers.capture());
    assertThat(body.getValue()).contains("\"event\":\"webhook.test\"");
    assertThat(body.getValue()).contains(businessId.toString());
    assertThat(headers.getValue())
        .containsEntry(WebhookSigner.SIGNATURE_HEADER, "deadbeef")
        .containsEntry("X-PGW-Event", "webhook.test");
    assertThat(headers.getValue()).containsKey("X-PGW-Trace-ID");
  }

  @Test
  void execute_reportsFailureOnNon2xxStatus() {
    UUID businessId = UUID.randomUUID();
    when(getBusinessWebhookConfigUseCase.execute(businessId))
        .thenReturn(new BusinessWebhookConfigDescriptor("https://merchant.example/webhook", "s"));
    when(webhookSigner.signatureFor(anyString(), anyString())).thenReturn("sig");
    when(transport.post(anyString(), anyString(), any()))
        .thenReturn(WebhookTestTransport.Result.success(503, 42L));

    TestWebhookResultDto result = useCase.execute(businessId);

    assertThat(result.isDelivered()).isFalse();
    assertThat(result.getStatusCode()).isEqualTo(503);
    assertThat(result.getError()).isNull();
  }

  @Test
  void execute_reportsTransportFailureWithErrorClassName() {
    UUID businessId = UUID.randomUUID();
    when(getBusinessWebhookConfigUseCase.execute(businessId))
        .thenReturn(new BusinessWebhookConfigDescriptor("https://merchant.example/webhook", "s"));
    when(webhookSigner.signatureFor(anyString(), anyString())).thenReturn("sig");
    when(transport.post(anyString(), anyString(), any()))
        .thenReturn(new WebhookTestTransport.Result(0, 15L, "SocketTimeoutException"));

    TestWebhookResultDto result = useCase.execute(businessId);

    assertThat(result.isDelivered()).isFalse();
    assertThat(result.getStatusCode()).isZero();
    assertThat(result.getError()).isEqualTo("SocketTimeoutException");
  }
}
