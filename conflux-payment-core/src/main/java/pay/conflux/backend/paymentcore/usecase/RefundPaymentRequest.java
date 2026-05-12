package pay.conflux.backend.paymentcore.usecase;

import java.util.Objects;
import java.util.UUID;
import pay.conflux.backend.common.money.Money;

/**
 * Request envelope for {@link RefundPaymentUseCase}.
 *
 * @param originalTransactionId the {@code Transaction.id} of the payment being refunded; must
 *     resolve to a {@code COMPLETED} transaction
 * @param amount refund amount and currency (currency must match the original); must be positive and
 *     {@code &le;} the original amount
 * @param reason free-form reason recorded for audit; surfaced verbatim to the vendor and the
 *     merchant webhook
 */
public record RefundPaymentRequest(UUID originalTransactionId, Money amount, String reason) {

  public RefundPaymentRequest {
    Objects.requireNonNull(originalTransactionId, "originalTransactionId must not be null");
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
  }
}
