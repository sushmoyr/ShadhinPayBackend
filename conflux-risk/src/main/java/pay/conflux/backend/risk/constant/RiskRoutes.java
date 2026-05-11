package pay.conflux.backend.risk.constant;

public final class RiskRoutes {
  public static final String ADMIN_RISK_BASE = "/api/v1/admin/risk";
  public static final String ADMIN_RISK_RULES = ADMIN_RISK_BASE + "/rules";
  public static final String ADMIN_RISK_RULE_BY_ID = ADMIN_RISK_RULES + "/{id}";
  public static final String ADMIN_RISK_BLACKLIST = ADMIN_RISK_BASE + "/blacklist";
  public static final String ADMIN_RISK_BLACKLIST_BY_ID = ADMIN_RISK_BLACKLIST + "/{id}";
  public static final String ADMIN_RISK_PROFILES = ADMIN_RISK_BASE + "/profiles";
  public static final String ADMIN_RISK_PROFILE_BY_ID = ADMIN_RISK_PROFILES + "/{merchantId}";
  public static final String ADMIN_RISK_CASES = ADMIN_RISK_BASE + "/cases";
  public static final String ADMIN_RISK_CASE_APPROVE = ADMIN_RISK_CASES + "/{evaluationId}/approve";
  public static final String ADMIN_RISK_CASE_REJECT = ADMIN_RISK_CASES + "/{evaluationId}/reject";

  private RiskRoutes() {}
}
