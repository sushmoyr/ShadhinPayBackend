package pay.conflux.backend.identity.constant;

import pay.conflux.backend.common.constant.Routes;

public final class IdentityRoutes {

  public static final String AUTH_BASE = Routes.V1.BASE + "/auth";
  public static final String AUTH_LOGIN = AUTH_BASE + "/login";

  public static final String MERCHANT_BASE = Routes.V1.BASE + "/merchant";
  public static final String MERCHANT_REGISTER = MERCHANT_BASE + "/register";
  public static final String MERCHANT_KYC = MERCHANT_BASE + "/kyc";
  public static final String MERCHANT_ME = MERCHANT_BASE + "/me";

  public static final String ADMIN_BASE = Routes.V1.BASE + "/admin";
  public static final String ADMIN_MERCHANTS = ADMIN_BASE + "/merchants";
  public static final String ADMIN_MERCHANTS_VERIFY = ADMIN_MERCHANTS + "/{id}/verify";
  public static final String ADMIN_MERCHANTS_REJECT = ADMIN_MERCHANTS + "/{id}/reject";
  public static final String ADMIN_USERS = ADMIN_BASE + "/users";
  public static final String ADMIN_USERS_BLOCK = ADMIN_USERS + "/{id}/block";
  public static final String ADMIN_USERS_UNBLOCK = ADMIN_USERS + "/{id}/unblock";

  public static final String AUTH_MFA_ENABLE = AUTH_BASE + "/mfa/enable";
  public static final String AUTH_MFA_VERIFY = AUTH_BASE + "/mfa/verify";
  public static final String AUTH_MFA_DISABLE = AUTH_BASE + "/mfa/disable";

  private IdentityRoutes() {}
}
