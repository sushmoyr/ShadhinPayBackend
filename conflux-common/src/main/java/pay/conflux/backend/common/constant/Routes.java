package pay.conflux.backend.common.constant;

public final class Routes {

  public static final String API_PREFIX = "/api";

  private Routes() {}

  public static final class V1 {

    public static final String BASE = API_PREFIX + "/v1";

    private V1() {}

    public static final class Admin {

      public static final String BASE = V1.BASE + "/admin";

      private Admin() {}

      // Per-feature route classes (e.g., Admin.Merchant, Admin.Payment) will be added by
      // feature agents in later prompts.
    }

    public static final class Public {

      public static final String BASE = V1.BASE;

      private Public() {}

      // Per-feature route classes (e.g., Public.Payment, Public.Webhook) will be added by
      // feature agents in later prompts.
    }
  }
}
