package com.shadhinpay.adapters.port;

/**
 * Outbound port that every MFS / card payment adapter implements.
 *
 * <p><b>Caller:</b> {@code payment-core} via {@code PaymentProviderRegistry#lookup(Vendor)}.
 *
 * <p><b>Implementation contract:</b>
 *
 * <ul>
 *   <li>Implementations are stateless Spring beans; one bean per vendor.
 *   <li>Each implementation owns an isolated {@code OkHttpClient} (built via {@code
 *       HttpClientFactory}) so a slow vendor cannot starve another vendor's connection pool.
 *   <li>Methods must never throw on vendor-side errors: map the vendor response into a {@link
 *       VendorResponse} with a populated {@link com.shadhinpay.common.error.ErrorCode}. Throw only
 *       on programmer errors (null required arguments, malformed credentials).
 *   <li>{@link VendorCredentials} are short-lived and must not be cached, persisted, or logged.
 * </ul>
 */
public interface PaymentProvider {

  /**
   * Initiates a new payment with the vendor.
   *
   * @param request transaction details
   * @param creds short-lived decrypted vendor credentials
   * @return unified {@link VendorResponse}; on success {@link VendorResponse#redirectUrl()} is
   *     populated
   */
  VendorResponse initiate(VendorPaymentRequest request, VendorCredentials creds);

  /**
   * Polls the vendor for the current state of an existing transaction. Used by the reconciliation
   * job for transactions stuck in {@link VendorStatus#PENDING}.
   *
   * @param vendorTrxId vendor-issued id from a prior {@code initiate} response
   * @param creds short-lived decrypted vendor credentials
   * @return unified {@link VendorResponse}
   */
  VendorResponse queryStatus(String vendorTrxId, VendorCredentials creds);

  /**
   * Reverses a completed transaction in part or in full.
   *
   * @param request refund details
   * @param creds short-lived decrypted vendor credentials
   * @return unified {@link VendorResponse}
   */
  VendorResponse refund(VendorRefundRequest request, VendorCredentials creds);

  /**
   * Predicate the registry uses to pick this adapter for a given {@link Vendor}.
   *
   * @param vendor candidate vendor
   * @return {@code true} if this adapter handles {@code vendor}; exactly one bean must return
   *     {@code true} for any supported vendor
   */
  boolean supports(Vendor vendor);
}
