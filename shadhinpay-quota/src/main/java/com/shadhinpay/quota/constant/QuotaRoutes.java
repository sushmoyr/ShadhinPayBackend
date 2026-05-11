package com.shadhinpay.quota.constant;

public final class QuotaRoutes {

  public static final String MERCHANT_ROOT = "/api/v1/merchant";
  public static final String MERCHANT_USAGE = MERCHANT_ROOT + "/usage";

  public static final String ADMIN_ROOT = "/api/v1/admin";
  public static final String ADMIN_QUOTA = ADMIN_ROOT + "/quota";

  private QuotaRoutes() {
    // Hidden constructor
  }
}
