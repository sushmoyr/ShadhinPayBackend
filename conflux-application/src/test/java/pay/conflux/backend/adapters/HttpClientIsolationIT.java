package pay.conflux.backend.adapters;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.support.HttpClientFactory;

class HttpClientIsolationIT {

  private static final Logger log = LoggerFactory.getLogger(HttpClientIsolationIT.class);

  private static final int TOTAL_CALLS = 100;
  private static final int WARMUP_PER_VENDOR = 5;

  /**
   * Bkash is stubbed at 200ms; an isolated nagad pool should respond essentially instantly. 75ms
   * absorbs CI/GC/scheduling jitter while still being &lt;40% of the slow-vendor latency — a
   * connection-pool starvation regression would push nagad p95 well past 200ms.
   */
  private static final long NAGAD_P95_BUDGET_MS = 75L;

  private WireMockServer bkashServer;
  private WireMockServer nagadServer;

  @BeforeEach
  void setUp() {
    bkashServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    bkashServer.start();
    // Register bkash stubs directly on its server instance — no static client state.
    bkashServer.stubFor(
        WireMock.post(WireMock.urlPathMatching("/bkash/.*"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withFixedDelay(200)
                    .withBody("{\"status\":\"success\"}")));

    nagadServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    nagadServer.start();
    nagadServer.stubFor(
        WireMock.post(WireMock.urlPathMatching("/nagad/.*"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody("{\"status\":\"success\"}")));
  }

  @AfterEach
  void tearDown() {
    if (bkashServer != null) {
      bkashServer.stop();
    }
    if (nagadServer != null) {
      nagadServer.stop();
    }
  }

  @Test
  void testIsolation() throws InterruptedException {
    HttpClientFactory factory = new HttpClientFactory();
    OkHttpClient bkashClient = factory.clientFor(Vendor.BKASH);
    OkHttpClient nagadClient = factory.clientFor(Vendor.NAGAD);

    Request bkashReq =
        new Request.Builder()
            .url(bkashServer.baseUrl() + "/bkash/pay")
            .post(RequestBody.create(new byte[0], null))
            .build();
    Request nagadReq =
        new Request.Builder()
            .url(nagadServer.baseUrl() + "/nagad/pay")
            .post(RequestBody.create(new byte[0], null))
            .build();

    // Warm-up: pay JIT, connection-pool init, and interceptor classloading out-of-band.
    for (int i = 0; i < WARMUP_PER_VENDOR; i++) {
      execAndDiscard(nagadClient, nagadReq);
      execAndDiscard(bkashClient, bkashReq);
    }

    ExecutorService executor = Executors.newFixedThreadPool(TOTAL_CALLS);
    List<Long> nagadLatencies = Collections.synchronizedList(new ArrayList<>());
    List<Long> bkashLatencies = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < TOTAL_CALLS; i++) {
      final int idx = i;
      executor.submit(
          () -> {
            try {
              if (idx % 2 == 0) {
                long start = System.currentTimeMillis();
                try (Response res = bkashClient.newCall(bkashReq).execute()) {
                  // discard
                }
                bkashLatencies.add(System.currentTimeMillis() - start);
              } else {
                long start = System.currentTimeMillis();
                try (Response res = nagadClient.newCall(nagadReq).execute()) {
                  // discard
                }
                nagadLatencies.add(System.currentTimeMillis() - start);
              }
            } catch (Exception e) {
              log.error("HTTP call failed", e);
              fail("HTTP call threw: " + e.getMessage());
            }
          });
    }

    executor.shutdown();
    boolean terminated = executor.awaitTermination(15, TimeUnit.SECONDS);
    assertTrue(terminated, "executor must terminate within 15s");

    List<Long> sortedNagad = new ArrayList<>(nagadLatencies);
    Collections.sort(sortedNagad);
    int p95Index = (int) Math.ceil(0.95 * sortedNagad.size()) - 1;
    long nagadP95 = sortedNagad.get(Math.max(0, p95Index));

    log.info("Nagad P95 latency: {} ms (n={})", nagadP95, sortedNagad.size());
    log.info("Bkash sample count: {}", bkashLatencies.size());

    assertTrue(
        nagadP95 < NAGAD_P95_BUDGET_MS,
        "Nagad P95 latency should be unaffected by slow Bkash calls, got " + nagadP95 + "ms");
  }

  private static void execAndDiscard(OkHttpClient client, Request req) {
    try (Response res = client.newCall(req).execute()) {
      // discard
    } catch (Exception e) {
      log.error("warm-up call failed", e);
      fail("warm-up call threw: " + e.getMessage());
    }
  }
}
