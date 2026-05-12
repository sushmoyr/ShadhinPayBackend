package pay.conflux.backend.provisioning.constant;

public enum Environment {
  TEST,
  LIVE;

  public String keyPrefix() {
    return this == LIVE ? "sp_live_" : "sp_test_";
  }
}
