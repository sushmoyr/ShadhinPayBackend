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
                "app_key", "AKIA-VERY-SECRET-KEY",
                "app_secret", "supersecretpassword123",
                "api_token", "Bearer-abcdef-1234567890"));

    String rendered = creds.toString();

    assertThat(rendered).doesNotContain("AKIA-VERY-SECRET-KEY");
    assertThat(rendered).doesNotContain("supersecretpassword123");
    assertThat(rendered).doesNotContain("Bearer-abcdef-1234567890");
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
