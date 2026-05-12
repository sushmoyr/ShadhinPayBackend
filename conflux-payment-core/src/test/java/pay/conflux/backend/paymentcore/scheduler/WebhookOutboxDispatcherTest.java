package pay.conflux.backend.paymentcore.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import pay.conflux.backend.paymentcore.entity.WebhookEventType;
import pay.conflux.backend.paymentcore.entity.WebhookOutbox;
import pay.conflux.backend.paymentcore.entity.WebhookOutboxStatus;
import pay.conflux.backend.paymentcore.repository.WebhookOutboxRepository;
import pay.conflux.backend.paymentcore.usecase.HandleWebhookRetryUseCase;

@ExtendWith(MockitoExtension.class)
class WebhookOutboxDispatcherTest {

  @Mock private WebhookOutboxRepository webhookOutboxRepository;
  @Mock private HandleWebhookRetryUseCase handleWebhookRetryUseCase;

  private WebhookOutbox row(UUID id) {
    return WebhookOutbox.builder()
        .id(id)
        .businessId(UUID.randomUUID())
        .transactionId(UUID.randomUUID())
        .eventType(WebhookEventType.PAYMENT_COMPLETED)
        .payload(java.util.Map.of("status", "COMPLETED"))
        .status(WebhookOutboxStatus.PENDING)
        .attemptCount(0)
        .nextAttemptAt(Instant.now())
        .build();
  }

  @Test
  void drain_dispatchesEachPendingRowOnce() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    when(webhookOutboxRepository.findAllByStatusAndNextAttemptAtBefore(
            eq(WebhookOutboxStatus.PENDING), any(Instant.class), any()))
        .thenReturn(List.of(row(id1), row(id2)));

    WebhookOutboxDispatcher dispatcher =
        new WebhookOutboxDispatcher(
            webhookOutboxRepository, handleWebhookRetryUseCase, new SyncTaskExecutor());

    dispatcher.drain();

    verify(handleWebhookRetryUseCase, times(1)).execute(id1);
    verify(handleWebhookRetryUseCase, times(1)).execute(id2);
  }

  @Test
  void drain_slowMerchantDoesNotBlockFastMerchant() throws InterruptedException {
    int slow = 10;
    int fast = 10;
    List<WebhookOutbox> rows = new ArrayList<>();
    List<UUID> slowIds = new ArrayList<>();
    List<UUID> fastIds = new ArrayList<>();
    for (int i = 0; i < slow; i++) {
      UUID id = UUID.randomUUID();
      slowIds.add(id);
      rows.add(row(id));
    }
    for (int i = 0; i < fast; i++) {
      UUID id = UUID.randomUUID();
      fastIds.add(id);
      rows.add(row(id));
    }
    when(webhookOutboxRepository.findAllByStatusAndNextAttemptAtBefore(
            eq(WebhookOutboxStatus.PENDING), any(Instant.class), any()))
        .thenReturn(rows);

    CountDownLatch fastDone = new CountDownLatch(fast);
    CountDownLatch slowRelease = new CountDownLatch(1);
    CountDownLatch slowDone = new CountDownLatch(slow);

    doAnswer(
            inv -> {
              UUID id = inv.getArgument(0);
              if (slowIds.contains(id)) {
                slowRelease.await();
                slowDone.countDown();
              } else {
                fastDone.countDown();
              }
              return null;
            })
        .when(handleWebhookRetryUseCase)
        .execute(any(UUID.class));

    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    // Pool large enough for both groups so slow tasks cannot starve fast ones.
    exec.setCorePoolSize(slow + fast);
    exec.setMaxPoolSize(slow + fast);
    exec.setQueueCapacity(100);
    exec.initialize();
    try {
      new WebhookOutboxDispatcher(webhookOutboxRepository, handleWebhookRetryUseCase, exec).drain();

      assertThat(fastDone.await(5, TimeUnit.SECONDS))
          .as("fast webhooks must complete while slow ones are still parked")
          .isTrue();

      slowRelease.countDown();
      assertThat(slowDone.await(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      exec.shutdown();
    }
  }
}
