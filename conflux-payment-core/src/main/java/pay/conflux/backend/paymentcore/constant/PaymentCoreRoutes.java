package pay.conflux.backend.paymentcore.constant;

/**
 * Compile-time route constants for the public payment-core HTTP surface. All controller
 * {@code @RequestMapping} annotations reference these — never inline path strings.
 */
public final class PaymentCoreRoutes {

  public static final String PAYMENTS = "/api/v1/payments";
  public static final String PAYMENT_BY_ID = "/api/v1/payments/{id}";
  public static final String PAYMENT_REFUND = "/api/v1/payments/{id}/refund";

  /**
   * Public-facing vendor return URL. {@code payment-core} 8b will implement the handler; the route
   * is declared here so the rest of the platform (security whitelist, OpenAPI tags) can refer to
   * it.
   */
  public static final String PAYMENT_CALLBACK = "/api/v1/payments/callback/{vendor}";

  public static final String HEADER_IDEMPOTENCY_KEY = "X-Idempotency-Key";
  public static final String HEADER_BUSINESS_ID = "X-Business-Id";

  private PaymentCoreRoutes() {}
}
