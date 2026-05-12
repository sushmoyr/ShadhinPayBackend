package pay.conflux.backend.paymentcore.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pay.conflux.backend.paymentcore.entity.Transaction;
import pay.conflux.backend.paymentcore.entity.TransactionStatus;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.usecase.ProcessVendorCallbackUseCase;

/**
 * Drains {@link TransactionStatus#PENDING_RECOVERY} rows by re-invoking the vendor's {@code
 * queryStatus} via {@link ProcessVendorCallbackUseCase}. Rows older than 24 h are handed to {@link
 * ReconciliationTimeoutFinalizer} — the <em>only</em> path that finalizes {@code PENDING_RECOVERY}
 * to {@code FAILED}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

  static final Duration STALE_THRESHOLD = Duration.ofSeconds(60);
  static final Duration TIMEOUT_THRESHOLD = Duration.ofHours(24);
  static final int POLL_BATCH_SIZE = 100;

  private final TransactionRepository transactionRepository;
  private final ProcessVendorCallbackUseCase processVendorCallbackUseCase;
  private final ReconciliationTimeoutFinalizer reconciliationTimeoutFinalizer;
  private final Clock clock;

  @Scheduled(fixedDelayString = "${conflux.payment-core.reconciliation.poll-interval-ms:30000}")
  public void reconcile() {
    Instant now = Instant.now(clock);
    LocalDateTime staleBefore = LocalDateTime.ofInstant(now.minus(STALE_THRESHOLD), ZoneOffset.UTC);
    List<Transaction> stuck =
        transactionRepository.findAllByStatusAndUpdatedAtBefore(
            TransactionStatus.PENDING_RECOVERY, staleBefore, PageRequest.of(0, POLL_BATCH_SIZE));
    if (stuck.isEmpty()) {
      return;
    }
    for (Transaction transaction : stuck) {
      try {
        if (hasTimedOut(transaction, now)) {
          reconciliationTimeoutFinalizer.finalizeTimeout(transaction.getId());
        } else {
          processVendorCallbackUseCase.resolveByTransactionId(transaction.getId());
          log.debug(
              "Reconciliation poll [transactionId={}, vendor={}]",
              transaction.getId(),
              transaction.getVendor());
        }
      } catch (RuntimeException e) {
        log.warn("Reconciliation poll threw [transactionId={}]", transaction.getId(), e);
      }
    }
  }

  private static boolean hasTimedOut(Transaction transaction, Instant now) {
    LocalDateTime created = transaction.getCreatedAt();
    if (created == null) {
      return false;
    }
    Instant createdAt = created.toInstant(ZoneOffset.UTC);
    return Duration.between(createdAt, now).compareTo(TIMEOUT_THRESHOLD) >= 0;
  }
}
