package com.shadhinpay.adapters.wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

class VendorWireMockExtensionTest {

  private static final OkHttpClient client = new OkHttpClient();

  @Test
  void afterEach_failsWhenUnmatchedRequestRecorded() throws IOException {
    VendorWireMockExtension ext = new VendorWireMockExtension();
    ExtensionContext ctx = mock(ExtensionContext.class);

    ext.beforeEach(ctx);

    // Fire a request to a path that was never stubbed.
    Request req =
        new Request.Builder()
            .url(ext.getBaseUrl() + "/unstubbed")
            .post(RequestBody.create(new byte[0], null))
            .build();
    try (Response res = client.newCall(req).execute()) {
      assertThat(res.code()).isEqualTo(404);
    }

    assertThatThrownBy(() -> ext.afterEach(ctx))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("unmatched");
  }

  @Test
  void afterEach_passesWhenAllRequestsMatched() throws IOException {
    VendorWireMockExtension ext = new VendorWireMockExtension();
    ExtensionContext ctx = mock(ExtensionContext.class);

    ext.beforeEach(ctx);

    WireMock.stubFor(post(urlPathMatching("/ok")).willReturn(aResponse().withStatus(200)));

    Request req =
        new Request.Builder()
            .url(ext.getBaseUrl() + "/ok")
            .post(RequestBody.create(new byte[0], null))
            .build();
    try (Response res = client.newCall(req).execute()) {
      assertThat(res.code()).isEqualTo(200);
    }

    assertThatCode(() -> ext.afterEach(ctx)).doesNotThrowAnyException();
  }

  @Test
  void getBaseUrl_returnsServerBaseUrl() {
    VendorWireMockExtension ext = new VendorWireMockExtension();
    ExtensionContext ctx = mock(ExtensionContext.class);

    ext.beforeEach(ctx);
    try {
      assertThat(ext.getBaseUrl()).startsWith("http://localhost:");
    } finally {
      ext.afterEach(ctx);
    }
  }
}
