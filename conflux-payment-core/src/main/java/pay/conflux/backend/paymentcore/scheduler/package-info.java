/**
 * Scheduled background workers owned by payment-core: webhook outbox drainer (8b), reconciliation
 * poller for {@code PENDING_RECOVERY} transactions (8b), and the hourly idempotency-record purge
 * (8b).
 */
package pay.conflux.backend.paymentcore.scheduler;
