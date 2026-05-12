package pay.conflux.backend.paymentcore.usecase;

import java.util.Objects;
import java.util.UUID;

/**
 * Outcome of a call to {@link ProcessVendorCallbackUseCase#execute(String, java.util.Map)}.
 *
 * @param transactionId resolved transaction id
 * @param status post-callback {@code Transaction.status}; kept as {@code String} to defer enum
 *     coupling across the module boundary
 */
public record ProcessVendorCallbackResult(UUID transactionId, String status) {

  public ProcessVendorCallbackResult {
    Objects.requireNonNull(transactionId, "transactionId must not be null");
    Objects.requireNonNull(status, "status must not be null");
  }
}
