package pay.conflux.backend.adapters.bkash;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import pay.conflux.backend.adapters.error.MfsAdapterException;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;
import pay.conflux.backend.adapters.support.HttpClientFactory;
import pay.conflux.backend.adapters.support.VendorAuthClient;

class BkashAuthClientTest {

  @RegisterExtension
  static final WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static final String GRANT_PATH = "/tokenized/checkout/token/grant";

  private BkashAuthClient client;

  @BeforeEach
  void setUp() {
    client = new BkashAuthClient(new HttpClientFactory(), new ObjectMapper());
  }

  private VendorCredentials creds() {
    return new VendorCredentials(
        Map.of(
            "appKey",
            "AK_TEST",
            "appSecret",
            "AS_TEST",
            "username",
            "u1",
            "password",
            "p1",
            "baseUrl",
            wm.baseUrl()));
  }

  @Test
  void authenticate_happyPath_returnsTokenWithSafetyMargin() {
    wm.stubFor(
        post(urlEqualTo(GRANT_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id_token\":\"abcd1234efgh5678\",\"expires_in\":3600,"
                            + "\"refresh_token\":\"rrr\",\"token_type\":\"Bearer\"}")));

    Instant before = Instant.now();
    VendorAuthClient.AuthToken token = client.authenticate(Vendor.BKASH, creds());
    Instant after = Instant.now();

    assertThat(token.token()).isEqualTo("abcd1234efgh5678");
    long lowerBoundSec = before.plusSeconds(3600 - 60 - 1).getEpochSecond();
    long upperBoundSec = after.plusSeconds(3600 - 60 + 1).getEpochSecond();
    assertThat(token.expiresAt().getEpochSecond()).isBetween(lowerBoundSec, upperBoundSec);
    wm.verify(
        1,
        postRequestedFor(urlEqualTo(GRANT_PATH))
            .withHeader("username", equalTo("u1"))
            .withHeader("password", equalTo("p1"))
            .withRequestBody(equalToJson("{\"app_key\":\"AK_TEST\",\"app_secret\":\"AS_TEST\"}")));
  }

  @Test
  void authenticate_401_throwsMfsAdapterException() {
    wm.stubFor(
        post(urlEqualTo(GRANT_PATH))
            .willReturn(aResponse().withStatus(401).withBody("{\"error\":\"bad creds\"}")));

    assertThatThrownBy(() -> client.authenticate(Vendor.BKASH, creds()))
        .isInstanceOf(MfsAdapterException.class)
        .hasMessageContaining("401");
  }

  @Test
  void authenticate_500_throwsMfsAdapterException() {
    wm.stubFor(post(urlEqualTo(GRANT_PATH)).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> client.authenticate(Vendor.BKASH, creds()))
        .isInstanceOf(MfsAdapterException.class)
        .hasMessageContaining("500");
  }

  @Test
  void authenticate_malformedJson_throwsMfsAdapterException() {
    wm.stubFor(
        post(urlEqualTo(GRANT_PATH))
            .willReturn(
                aResponse().withHeader("Content-Type", "text/plain").withBody("not really json")));

    assertThatThrownBy(() -> client.authenticate(Vendor.BKASH, creds()))
        .isInstanceOf(MfsAdapterException.class);
  }

  @Test
  void authenticate_responseMissingIdToken_throwsMfsAdapterException() {
    wm.stubFor(
        post(urlEqualTo(GRANT_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"expires_in\":3600}")));

    assertThatThrownBy(() -> client.authenticate(Vendor.BKASH, creds()))
        .isInstanceOf(MfsAdapterException.class)
        .hasMessageContaining("malformed");
  }

  @Test
  void authenticate_nonBkashVendor_throwsIllegalArgument() {
    assertThatThrownBy(() -> client.authenticate(Vendor.MOCK, creds()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("BKASH");
  }

  @Test
  void authenticate_missingCredential_throwsIllegalArgument() {
    VendorCredentials partial =
        new VendorCredentials(
            Map.of("appKey", "x", "appSecret", "y", "username", "u", "baseUrl", wm.baseUrl()));
    assertThatThrownBy(() -> client.authenticate(Vendor.BKASH, partial))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("password");
  }
}
