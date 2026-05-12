package pay.conflux.backend.paymentcore.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pay.conflux.backend.paymentcore.entity.Transaction;
import pay.conflux.backend.paymentcore.entity.TransactionMode;
import pay.conflux.backend.paymentcore.entity.TransactionStatus;
import pay.conflux.backend.paymentcore.entity.WebhookOutbox;
import pay.conflux.backend.paymentcore.events.PaymentFailedEvent;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.repository.WebhookOutboxRepository;
import pay.conflux.backend.paymentcore.usecase.ProcessVendorCallbackResult;
import pay.conflux.backend.paymentcore.usecase.ProcessVendorCallbackUseCase;

@ExtendWith(MockitoExtension.class)
class ReconciliationSchedulerTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private WebhookOutboxRepository webhookOutboxRepository;
  @Mock private ProcessVendorCallbackUseCase processVendorCallbackUseCase;
  @Mock private ApplicationEventPublisher eventPublisher;

  private Transaction stuck(UUID id, LocalDateTime createdAt) {
    Transaction tx =
        Transaction.builder()
            .id(id)
            .businessId(UUID.randomUUID())
            .merchantId(UUID.randomUUID())
            .amountValue(new BigDecimal("100"))
            .amountCurrency("BDT")
            .status(TransactionStatus.PENDING_RECOVERY)
            .vendor("MOCK")
            .mode(TransactionMode.PARTNER)
            .merchantOrderReference("o1")
            .retryCount(0)
            .metadata(new HashMap<>())
            .build();
    tx.setCreatedAt(createdAt);
    return tx;
  }

  @Test
  void reconcile_recentRow_delegatesToProcessVendorCallback() {
    Clock clock = Clock.fixed(Instant.parse("2026-05-12T12:00:00Z"), ZoneOffset.UTC);
    UUID id = UUID.randomUUID();
    Transaction tx = stuck(id, LocalDateTime.now(clock).minusMinutes(5));
    when(transactionRepository.findAllByStatusAndUpdatedAtBefore(
            eq(TransactionStatus.PENDING_RECOVERY), any(LocalDateTime.class), any()))
        .thenReturn(List.of(tx));
    when(processVendorCallbackUseCase.resolveByTransactionId(id))
        .thenReturn(new ProcessVendorCallbackResult(id, "COMPLETED"));

    ReconciliationTimeoutFinalizer finalizer =
        new ReconciliationTimeoutFinalizer(
            transactionRepository, webhookOutboxRepository, eventPublisher, clock);
    new ReconciliationScheduler(
            transactionRepository, processVendorCallbackUseCase, finalizer, clock)
        .reconcile();

    verify(processVendorCallbackUseCase, times(1)).resolveByTransactionId(id);
  }

  @Test
  void reconcile_25hOldRow_finalizesAsFailedWithTimeoutFlag() {
    Clock clock = Clock.fixed(Instant.parse("2026-05-12T12:00:00Z"), ZoneOffset.UTC);
    UUID id = UUID.randomUUID();
    Transaction tx = stuck(id, LocalDateTime.now(clock).minusHours(25));
    when(transactionRepository.findAllByStatusAndUpdatedAtBefore(
            eq(TransactionStatus.PENDING_RECOVERY), any(LocalDateTime.class), any()))
        .thenReturn(List.of(tx));
    when(transactionRepository.findById(id)).thenReturn(Optional.of(tx));
    when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

    ReconciliationTimeoutFinalizer finalizer =
        new ReconciliationTimeoutFinalizer(
            transactionRepository, webhookOutboxRepository, eventPublisher, clock);
    new ReconciliationScheduler(
            transactionRepository, processVendorCallbackUseCase, finalizer, clock)
        .reconcile();

    verify(processVendorCallbackUseCase, never()).resolveByTransactionId(any());
    ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).save(txCaptor.capture());
    assertThat(txCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.FAILED);
    assertThat(txCaptor.getValue().getMetadata())
        .containsEntry(ReconciliationTimeoutFinalizer.RECONCILIATION_TIMEOUT_FLAG, "true");
    verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
    verify(webhookOutboxRepository).save(any(WebhookOutbox.class));
  }

  @Test
  void reconcile_emptyPoll_noop() {
    Clock clock = Clock.fixed(Instant.parse("2026-05-12T12:00:00Z"), ZoneOffset.UTC);
    when(transactionRepository.findAllByStatusAndUpdatedAtBefore(
            eq(TransactionStatus.PENDING_RECOVERY), any(LocalDateTime.class), any()))
        .thenReturn(List.of());

    ReconciliationTimeoutFinalizer finalizer =
        new ReconciliationTimeoutFinalizer(
            transactionRepository, webhookOutboxRepository, eventPublisher, clock);
    new ReconciliationScheduler(
            transactionRepository, processVendorCallbackUseCase, finalizer, clock)
        .reconcile();

    verify(processVendorCallbackUseCase, never()).resolveByTransactionId(any());
  }
}
