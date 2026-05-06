package com.shadhinpay.adapters.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class VendorCredentialsTest {

  @Test
  void toString_redacts_every_credential_value() {
    VendorCredentials creds =
        new VendorCredentials(
            Map.of(
                "app_key", "fake-app-key-aaa",
                "app_secret", "fake-app-secret-aaa",
                "api_token", "fake-api-token-aaa"));

    String rendered = creds.toString();

    assertThat(rendered).doesNotContain("fake-app-key-aaa");
    assertThat(rendered).doesNotContain("fake-app-secret-aaa");
    assertThat(rendered).doesNotContain("fake-api-token-aaa");
    assertThat(rendered).contains("app_key");
    assertThat(rendered).contains("app_secret");
    assertThat(rendered).contains("api_token");
    assertThat(rendered).contains("REDACTED");
  }

  @Test
  void toString_with_empty_values_still_redacts() {
    VendorCredentials creds = new VendorCredentials(Map.of());
    assertThat(creds.toString()).contains("REDACTED").contains("keys=[]");
  }

  @Test
  void values_map_is_immutable() {
    java.util.Map<String, String> mutable = new java.util.HashMap<>();
    mutable.put("k", "v");
    VendorCredentials creds = new VendorCredentials(mutable);
    mutable.put("k2", "v2");
    assertThat(creds.values()).hasSize(1).containsEntry("k", "v");
  }
}
