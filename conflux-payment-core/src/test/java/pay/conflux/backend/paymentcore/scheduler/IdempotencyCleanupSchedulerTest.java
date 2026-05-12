package pay.conflux.backend.paymentcore.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.paymentcore.repository.IdempotencyRecordRepository;

@ExtendWith(MockitoExtension.class)
class IdempotencyCleanupSchedulerTest {

  @Mock private IdempotencyRecordRepository repository;

  @Test
  void purgeExpired_delegatesToRepository() {
    Clock clock = Clock.fixed(Instant.parse("2026-05-12T12:00:00Z"), ZoneOffset.UTC);
    when(repository.deleteAllByExpiresAtBefore(any(Instant.class))).thenReturn(7);

    new IdempotencyCleanupScheduler(repository, clock).purgeExpired();

    verify(repository).deleteAllByExpiresAtBefore(Instant.parse("2026-05-12T12:00:00Z"));
  }
}
