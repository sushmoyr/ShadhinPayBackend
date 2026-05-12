package pay.conflux.backend.paymentcore.scheduler;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.error.ErrorCode;
import pay.conflux.backend.paymentcore.entity.Transaction;
import pay.conflux.backend.paymentcore.entity.TransactionStatus;
import pay.conflux.backend.paymentcore.entity.WebhookEventType;
import pay.conflux.backend.paymentcore.entity.WebhookOutbox;
import pay.conflux.backend.paymentcore.entity.WebhookOutboxStatus;
import pay.conflux.backend.paymentcore.events.PaymentFailedEvent;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.repository.WebhookOutboxRepository;

/**
 * The sanctioned path for finalizing a {@code PENDING_RECOVERY} transaction to {@code FAILED} after
 * the 24-hour reconciliation timeout. Lives in a separate bean so the {@code REQUIRES_NEW}
 * transaction boundary is honored (Spring proxies do not intercept self-invocation).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationTimeoutFinalizer {

  static final String RECONCILIATION_TIMEOUT_FLAG = "reconciliation_timeout";

  private final TransactionRepository transactionRepository;
  private final WebhookOutboxRepository webhookOutboxRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void finalizeTimeout(UUID transactionId) {
    Transaction tx = transactionRepository.findById(transactionId).orElse(null);
    if (tx == null || tx.getStatus() != TransactionStatus.PENDING_RECOVERY) {
      return;
    }
    Map<String, String> metadata =
        tx.getMetadata() == null ? new HashMap<>() : new HashMap<>(tx.getMetadata());
    metadata.put(RECONCILIATION_TIMEOUT_FLAG, "true");
    tx.setMetadata(metadata);
    tx.setStatus(TransactionStatus.FAILED);
    transactionRepository.save(tx);

    eventPublisher.publishEvent(
        new PaymentFailedEvent(
            tx.getId(),
            tx.getMerchantId(),
            tx.getBusinessId(),
            tx.getVendor(),
            ErrorCode.VENDOR_DOWN,
            "reconciliation timeout (24h)",
            Map.copyOf(metadata),
            Instant.now(clock),
            traceId()));

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("transactionId", tx.getId().toString());
    payload.put("status", tx.getStatus().name());
    payload.put("amount", tx.getAmountValue());
    payload.put("currency", tx.getAmountCurrency());
    payload.put("vendor", tx.getVendor());
    payload.put("merchantOrderReference", tx.getMerchantOrderReference());
    payload.put("eventType", WebhookEventType.PAYMENT_FAILED.name());
    payload.put("reason", "reconciliation_timeout");
    webhookOutboxRepository.save(
        WebhookOutbox.builder()
            .transactionId(tx.getId())
            .businessId(tx.getBusinessId())
            .eventType(WebhookEventType.PAYMENT_FAILED)
            .payload(payload)
            .status(WebhookOutboxStatus.PENDING)
            .attemptCount(0)
            .nextAttemptAt(Instant.now(clock))
            .build());

    log.info(
        "Reconciliation timeout — transaction finalized as FAILED [transactionId={}, vendor={}]",
        tx.getId(),
        tx.getVendor());
  }

  private static String traceId() {
    String value = MDC.get("traceId");
    return value == null ? "-" : value;
  }
}
