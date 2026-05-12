package pay.conflux.backend.paymentcore.usecase;

/**
 * Refunds a previously-completed payment. Persists a new {@code Transaction} row representing the
 * refund leg, dispatches to the vendor adapter, and (on success) publishes {@code
 * PaymentRefundedEvent} and enqueues a {@code PAYMENT_REFUNDED} webhook.
 *
 * <p>Refunds are not idempotent at the use-case level — issue an {@code X-Idempotency-Key} at the
 * REST boundary if the merchant needs replay safety (Phase 1 §6).
 */
public interface RefundPaymentUseCase {

  RefundPaymentResult execute(RefundPaymentRequest request);
}
