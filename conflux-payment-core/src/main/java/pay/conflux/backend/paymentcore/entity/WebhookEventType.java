package pay.conflux.backend.paymentcore.entity;

/** Event-type discriminator for rows in the {@code webhook_outbox} table. */
public enum WebhookEventType {
  PAYMENT_INITIATED,
  PAYMENT_COMPLETED,
  PAYMENT_FAILED,
  PAYMENT_REFUNDED,
  WEBHOOK_PING
}
