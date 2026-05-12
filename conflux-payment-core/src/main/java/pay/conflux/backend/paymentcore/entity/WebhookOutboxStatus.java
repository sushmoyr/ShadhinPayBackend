package pay.conflux.backend.paymentcore.entity;

/** Delivery state of a {@link WebhookOutbox} row. */
public enum WebhookOutboxStatus {
  PENDING,
  SENT,
  FAILED
}
