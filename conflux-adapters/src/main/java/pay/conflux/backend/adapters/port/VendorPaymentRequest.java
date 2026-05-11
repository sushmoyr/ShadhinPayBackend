package pay.conflux.backend.adapters.port;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import pay.conflux.backend.common.money.Money;

/**
 * Input to {@link PaymentProvider#initiate(VendorPaymentRequest, VendorCredentials)}.
 *
 * <p>Built by {@code payment-core} after pre-flight (provisioning + risk + quota) and handed off to
 * the selected adapter. Carries no PII beyond what the vendor strictly requires; sensitive customer
 * data is fetched by the vendor on its own redirect page.
 *
 * @param transactionId internal ConfluxPay transaction id; echoed back to the vendor as the
 *     external reference and used to correlate webhooks
 * @param amount transaction amount and currency
 * @param merchantOrderReference merchant-supplied order reference (free-form, &le; 255 chars)
 * @param callbackUrl URL the vendor should redirect the customer back to after checkout
 * @param metadata immutable bag of additional vendor-specific hints (e.g. {@code "channel"}, {@code
 *     "device_id"})
 */
@SuppressWarnings("PMD.UnusedAssignment")
public record VendorPaymentRequest(
    UUID transactionId,
    Money amount,
    String merchantOrderReference,
    String callbackUrl,
    Map<String, String> metadata) {

  public VendorPaymentRequest {
    Objects.requireNonNull(transactionId, "transactionId must not be null");
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(merchantOrderReference, "merchantOrderReference must not be null");
    Objects.requireNonNull(callbackUrl, "callbackUrl must not be null");
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
