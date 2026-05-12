package pay.conflux.backend.paymentcore.entity;

/**
 * Lifecycle states for a {@link Transaction}. The enum is persisted as {@code TEXT} (via {@code
 * EnumType.STRING}) — never as ordinal — and the DB-side {@code CHECK} constraint mirrors this set.
 */
public enum TransactionStatus {
  INITIATED,
  PENDING,
  COMPLETED,
  FAILED,
  CANCELLED,
  PENDING_RECOVERY,
  PENDING_RISK
}
