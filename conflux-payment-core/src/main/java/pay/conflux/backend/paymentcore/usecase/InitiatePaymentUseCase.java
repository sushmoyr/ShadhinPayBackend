package pay.conflux.backend.paymentcore.usecase;

/**
 * Initiates a payment: validates idempotency, runs pre-flight checks (provisioning/quota/risk),
 * persists the {@code Transaction}, dispatches to the MFS adapter, and returns the redirect URL.
 *
 * <p><b>Caller:</b> the public REST controller in {@code application} for direct merchant API
 * calls, and the {@code invoice} module when a customer pays a hosted invoice.
 *
 * <p><b>Guarantees:</b>
 *
 * <ul>
 *   <li><b>Idempotent</b> on {@code (businessId, idempotencyKey)}: a duplicate request returns the
 *       original {@link PaymentInitiationResult} without producing a second {@code Transaction}
 *       row.
 *   <li>Pre-flight ordering: provisioning lookup → quota reservation (partner mode only) → risk
 *       evaluation → adapter dispatch. A risk {@code BLOCK} short-circuits before any MFS call.
 *   <li>Reliable delivery: a {@code WebhookOutbox} row is enqueued in the same transaction as the
 *       {@code Transaction} and {@code PaymentInitiatedEvent}.
 * </ul>
 */
public interface InitiatePaymentUseCase {

  /**
   * Initiates a payment for the supplied request.
   *
   * @param request fully-validated {@link InitiatePaymentRequest}
   * @return a {@link PaymentInitiationResult} carrying the new (or replayed) transaction id and
   *     redirect URL; never {@code null}
   */
  PaymentInitiationResult execute(InitiatePaymentRequest request);
}
