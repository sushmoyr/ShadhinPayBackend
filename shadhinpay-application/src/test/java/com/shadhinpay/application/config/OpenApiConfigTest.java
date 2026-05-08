package com.shadhinpay.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

  @Test
  void shadhinPayOpenAPI_returns_configured_instance() {
    OpenAPI api = new OpenApiConfig().shadhinPayOpenAPI();

    assertThat(api).isNotNull();
    assertThat(api.getInfo().getTitle()).isEqualTo("ShadhinPay API");
    assertThat(api.getInfo().getVersion()).isEqualTo("v1");
    assertThat(api.getComponents().getSecuritySchemes()).containsKey("ApiKeyAuth");
    assertThat(api.getSecurity()).isNotEmpty();
  }
}
