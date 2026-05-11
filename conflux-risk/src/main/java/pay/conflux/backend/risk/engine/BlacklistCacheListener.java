package pay.conflux.backend.risk.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pay.conflux.backend.risk.events.BlacklistEntryChangedEvent;

/**
 * Mirrors blacklist mutations to the Redis SET held by {@link BlacklistCache}. Runs after the
 * publishing transaction commits.
 *
 * <p>Fails OPEN: any Redis exception is logged at ERROR and swallowed. The scheduled re-hydration
 * inside {@link BlacklistCache#scheduledHydrate()} bounds drift; the evaluation pipeline never
 * treats a cache miss as ALLOW.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlacklistCacheListener {

  private final BlacklistCache cache;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(BlacklistEntryChangedEvent event) {
    try {
      switch (event.kind()) {
        case ADDED -> cache.add(event.type(), event.value());
        case REMOVED -> cache.remove(event.type(), event.value());
        default -> log.warn("Unhandled BlacklistEntryChangedEvent kind {}", event.kind());
      }
    } catch (Exception e) {
      log.error(
          "BlacklistCache hot-update failed for type {} ({}): {}",
          event.type(),
          event.kind(),
          e.getMessage(),
          e);
    }
  }
}
