package pay.conflux.backend.provisioning.constant;

public final class ProvisioningRoutes {

  public static final String MERCHANT_BUSINESSES = "/api/v1/merchant/businesses";
  public static final String MERCHANT_BUSINESS_BY_ID = "/api/v1/merchant/businesses/{id}";
  public static final String MERCHANT_BUSINESS_VENDORS = "/api/v1/merchant/businesses/{id}/vendors";
  public static final String MERCHANT_BUSINESS_APIKEYS =
      "/api/v1/merchant/businesses/{id}/api-keys";
  public static final String MERCHANT_BUSINESS_APIKEY_BY_ID =
      "/api/v1/merchant/businesses/{id}/api-keys/{keyId}";
  public static final String MERCHANT_BUSINESS_APIKEY_ROTATE =
      "/api/v1/merchant/businesses/{id}/api-keys/{keyId}/rotate";
  public static final String MERCHANT_BUSINESS_WEBHOOK = "/api/v1/merchant/businesses/{id}/webhook";
  public static final String MERCHANT_BUSINESS_WEBHOOK_TEST =
      "/api/v1/merchant/businesses/{id}/webhook/test";

  public static final String ADMIN_BUSINESSES = "/api/v1/admin/businesses";
  public static final String ADMIN_BUSINESS_BY_ID = "/api/v1/admin/businesses/{id}";
  public static final String ADMIN_BUSINESS_DEACTIVATE = "/api/v1/admin/businesses/{id}/deactivate";
  public static final String ADMIN_BUSINESS_ACTIVATE = "/api/v1/admin/businesses/{id}/activate";

  private ProvisioningRoutes() {}
}
