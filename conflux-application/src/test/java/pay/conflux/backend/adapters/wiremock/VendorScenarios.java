package pay.conflux.backend.adapters.wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import pay.conflux.backend.adapters.port.Vendor;

public class VendorScenarios {

  public static void success(Vendor v) {
    stubFor(
        post(urlPathMatching("/" + v.name().toLowerCase() + "/.*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"success\"}")));
  }

  public static void vendorError(Vendor v, String code) {
    stubFor(
        post(urlPathMatching("/" + v.name().toLowerCase() + "/.*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"error\", \"code\":\"" + code + "\"}")));
  }

  public static void timeout(Vendor v, long ms) {
    stubFor(
        post(urlPathMatching("/" + v.name().toLowerCase() + "/.*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"success\"}")
                    .withFixedDelay((int) ms)));
  }

  public static void unauthorizedThenSuccess(Vendor v) {
    stubFor(
        post(urlPathMatching("/" + v.name().toLowerCase() + "/.*"))
            .inScenario("Auth")
            .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
            .willReturn(aResponse().withStatus(401))
            .willSetStateTo("Authorized"));

    stubFor(
        post(urlPathMatching("/" + v.name().toLowerCase() + "/.*"))
            .inScenario("Auth")
            .whenScenarioStateIs("Authorized")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"success\"}")));
  }
}
