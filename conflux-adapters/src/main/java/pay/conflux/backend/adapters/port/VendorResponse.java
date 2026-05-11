package pay.conflux.backend.adapters.port;

import java.util.Objects;
import pay.conflux.backend.common.error.ErrorCode;

/**
 * Unified response model returned by every {@link PaymentProvider} method.
 *
 * <p>Adapters translate vendor-native payloads into this single shape so that {@code payment-core}
 * never needs to know which MFS was called.
 *
 * @param status mapped {@link VendorStatus}; never {@code null}
 * @param vendorTrxId vendor-issued transaction id (e.g. bKash {@code paymentID}, Stripe session
 *     id); may be {@code null} if the vendor failed before issuing one
 * @param redirectUrl URL to send the customer to in order to complete checkout; populated by {@link
 *     PaymentProvider#initiate(VendorPaymentRequest, VendorCredentials)} on success and {@code
 *     null} for {@code queryStatus} / {@code refund}
 * @param rawResponse raw JSON body returned by the vendor, retained verbatim for debugging / audit;
 *     may be {@code null} on transport-level failures
 * @param errorCode normalised error category; {@code null} on success, populated whenever {@code
 *     status} is {@link VendorStatus#FAILED} or {@link VendorStatus#UNKNOWN}
 */
public record VendorResponse(
    VendorStatus status,
    String vendorTrxId,
    String redirectUrl,
    String rawResponse,
    ErrorCode errorCode) {

  public VendorResponse {
    Objects.requireNonNull(status, "status must not be null");
  }
}
