package pay.conflux.backend.adapters.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class VendorWireMockExtension implements BeforeEachCallback, AfterEachCallback {

  private WireMockServer server;

  @Override
  public void beforeEach(ExtensionContext context) {
    server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    server.start();
    WireMock.configureFor("localhost", server.port());
  }

  @Override
  public void afterEach(ExtensionContext context) {
    if (server != null && server.isRunning()) {
      try {
        var unmatched = server.findAllUnmatchedRequests();
        if (!unmatched.isEmpty()) {
          throw new AssertionError("WireMock found unmatched requests: " + unmatched);
        }
      } finally {
        server.stop();
      }
    }
  }

  public String getBaseUrl() {
    return server.baseUrl();
  }
}
