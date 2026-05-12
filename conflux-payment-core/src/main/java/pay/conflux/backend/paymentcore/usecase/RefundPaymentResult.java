package pay.conflux.backend.paymentcore.usecase;

import java.util.Objects;
import java.util.UUID;

/**
 * Outcome of {@link RefundPaymentUseCase#execute(RefundPaymentRequest)}.
 *
 * @param refundTransactionId the new {@code Transaction.id} that represents the refund leg
 * @param originalTransactionId the original payment being refunded
 * @param status post-call status of the refund transaction ({@code COMPLETED}, {@code FAILED},
 *     {@code PENDING}, {@code PENDING_RECOVERY})
 */
public record RefundPaymentResult(
    UUID refundTransactionId, UUID originalTransactionId, String status) {

  public RefundPaymentResult {
    Objects.requireNonNull(refundTransactionId, "refundTransactionId must not be null");
    Objects.requireNonNull(originalTransactionId, "originalTransactionId must not be null");
    Objects.requireNonNull(status, "status must not be null");
  }
}
