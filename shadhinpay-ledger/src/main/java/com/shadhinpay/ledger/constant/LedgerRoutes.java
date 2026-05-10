package com.shadhinpay.ledger.constant;

import com.shadhinpay.common.constant.Routes;

public final class LedgerRoutes {
  private LedgerRoutes() {}

  public static final String MERCHANT_BASE = Routes.V1.BASE + "/merchant";

  public static final String MERCHANT_BALANCE = MERCHANT_BASE + "/balance";
  public static final String MERCHANT_JOURNAL = MERCHANT_BASE + "/journal";

  public static final String ADMIN_LEDGER_BASE = Routes.V1.Admin.BASE + "/ledger";

  public static final String ADMIN_JOURNAL = ADMIN_LEDGER_BASE + "/journal";
  public static final String ADMIN_BALANCE = ADMIN_LEDGER_BASE + "/balance";
  public static final String ADMIN_TRIAL_BALANCE = ADMIN_LEDGER_BASE + "/trial-balance";
}
