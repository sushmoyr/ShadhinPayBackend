package pay.conflux.backend.identity.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UserBlockedEvent(UUID userId, String reason, Instant occurredAt, String traceId) {

  public UserBlockedEvent {
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(traceId, "traceId must not be null");
  }
}
