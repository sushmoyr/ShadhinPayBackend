package pay.conflux.backend.risk.events;

import pay.conflux.backend.risk.enums.BlacklistType;

/**
 * Domain event signalling that a {@code BlacklistEntry} has been added or removed. Consumed
 * post-commit by {@code BlacklistCacheListener} to mirror the change to the Redis SET.
 *
 * <p>Carries the full payload needed for cache mutation; no DB re-query in the listener.
 */
public record BlacklistEntryChangedEvent(BlacklistType type, String value, ChangeKind kind) {

  public enum ChangeKind {
    ADDED,
    REMOVED
  }
}
