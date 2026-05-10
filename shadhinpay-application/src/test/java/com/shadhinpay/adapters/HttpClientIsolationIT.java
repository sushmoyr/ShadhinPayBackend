package com.shadhinpay.adapters;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.shadhinpay.adapters.port.Vendor;
import com.shadhinpay.adapters.support.HttpClientFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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

class HttpClientIsolationIT {

  private WireMockServer bkashServer;
  private WireMockServer nagadServer;

  @BeforeEach
  void setUp() {
    bkashServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    bkashServer.start();
    WireMock.configureFor("localhost", bkashServer.port());
    WireMock.stubFor(
        WireMock.post(WireMock.urlPathMatching("/bkash/.*"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withFixedDelay(200) // Slow response
                    .withBody("{\"status\":\"success\"}")));

    nagadServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    nagadServer.start();
    WireMock.configureFor("localhost", nagadServer.port());
    WireMock.stubFor(
        WireMock.post(WireMock.urlPathMatching("/nagad/.*"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withBody("{\"status\":\"success\"}"))); // Immediate response
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

    int totalCalls = 50;
    CountDownLatch latch = new CountDownLatch(totalCalls);
    ExecutorService executor = Executors.newFixedThreadPool(totalCalls);

    List<Long> nagadLatencies = Collections.synchronizedList(new ArrayList<>());
    List<Long> bkashLatencies = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < totalCalls; i++) {
      final int idx = i;
      executor.submit(
          () -> {
            try {
              if (idx % 2 == 0) {
                // bkash call
                long start = System.currentTimeMillis();
                Request req =
                    new Request.Builder()
                        .url(bkashServer.baseUrl() + "/bkash/pay")
                        .post(RequestBody.create(new byte[0], null))
                        .build();
                try (Response res = bkashClient.newCall(req).execute()) {
                  // ignore response
                }
                bkashLatencies.add(System.currentTimeMillis() - start);
              } else {
                // nagad call
                long start = System.currentTimeMillis();
                Request req =
                    new Request.Builder()
                        .url(nagadServer.baseUrl() + "/nagad/pay")
                        .post(RequestBody.create(new byte[0], null))
                        .build();
                try (Response res = nagadClient.newCall(req).execute()) {
                  // ignore response
                }
                nagadLatencies.add(System.currentTimeMillis() - start);
              }
            } catch (Exception e) {
              e.printStackTrace();
            } finally {
              latch.countDown();
            }
          });
    }

    latch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    // Calculate p95 latency for Nagad
    List<Long> sortedNagad = new ArrayList<>(nagadLatencies);
    Collections.sort(sortedNagad);
    int p95Index = (int) Math.ceil(95 / 100.0 * sortedNagad.size()) - 1;
    long nagadP95 = sortedNagad.get(Math.max(0, p95Index));

    System.out.println("Nagad P95 Latency: " + nagadP95 + " ms");
    System.out.println("Bkash Latencies: " + bkashLatencies);

    assertTrue(nagadP95 < 50, "Nagad P95 latency should be unaffected by slow Bkash calls");
  }
}
