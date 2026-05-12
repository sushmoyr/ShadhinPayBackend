package pay.conflux.backend.paymentcore.scheduler;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pay.conflux.backend.paymentcore.config.PaymentCoreConfig;
import pay.conflux.backend.paymentcore.entity.WebhookOutbox;
import pay.conflux.backend.paymentcore.entity.WebhookOutboxStatus;
import pay.conflux.backend.paymentcore.repository.WebhookOutboxRepository;
import pay.conflux.backend.paymentcore.usecase.HandleWebhookRetryUseCase;

/**
 * Polls {@code webhook_outbox} for {@code PENDING} rows whose {@code nextAttemptAt} is in the past
 * and submits each to the {@code webhookExecutor} for delivery.
 *
 * <p>Each dispatch runs in its own thread + its own DB transaction (the {@link
 * HandleWebhookRetryUseCase} impl carries {@code REQUIRES_NEW}) so a slow merchant cannot back up
 * the poll loop. The executor is dedicated to webhooks — not shared with cross-module event
 * handlers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookOutboxDispatcher {

  static final int POLL_BATCH_SIZE = 50;

  private final WebhookOutboxRepository webhookOutboxRepository;
  private final HandleWebhookRetryUseCase handleWebhookRetryUseCase;

  @Qualifier(PaymentCoreConfig.WEBHOOK_EXECUTOR)
  private final TaskExecutor webhookExecutor;

  @Scheduled(fixedDelayString = "${conflux.payment-core.webhook.poll-interval-ms:5000}")
  public void drain() {
    List<WebhookOutbox> due =
        webhookOutboxRepository.findAllByStatusAndNextAttemptAtBefore(
            WebhookOutboxStatus.PENDING, Instant.now(), PageRequest.of(0, POLL_BATCH_SIZE));
    if (due.isEmpty()) {
      return;
    }
    for (WebhookOutbox row : due) {
      UUID id = row.getId();
      webhookExecutor.execute(
          () -> {
            try {
              handleWebhookRetryUseCase.execute(id);
            } catch (RuntimeException e) {
              log.warn("Webhook dispatch threw on row {}", id, e);
            }
          });
    }
  }
}
