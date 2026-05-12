package pay.conflux.backend.paymentcore.scheduler;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.paymentcore.repository.IdempotencyRecordRepository;

/**
 * Hourly purge of expired {@code idempotency_records} rows. The Redis L1 entries TTL themselves
 * out; this scheduler only owns the durable L2 store.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupScheduler {

  private final IdempotencyRecordRepository idempotencyRecordRepository;
  private final Clock clock;

  @Scheduled(cron = "${conflux.payment-core.idempotency.cleanup-cron:0 0 * * * *}")
  @Transactional
  public void purgeExpired() {
    int deleted = idempotencyRecordRepository.deleteAllByExpiresAtBefore(Instant.now(clock));
    if (deleted > 0) {
      log.info("Idempotency cleanup removed {} expired rows", deleted);
    }
  }
}
