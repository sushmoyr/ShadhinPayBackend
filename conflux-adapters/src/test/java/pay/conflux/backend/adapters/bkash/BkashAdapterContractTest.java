package pay.conflux.backend.adapters.bkash;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;
import pay.conflux.backend.adapters.port.VendorPaymentRequest;
import pay.conflux.backend.adapters.port.VendorRefundRequest;
import pay.conflux.backend.adapters.port.VendorResponse;
import pay.conflux.backend.adapters.port.VendorStatus;
import pay.conflux.backend.adapters.support.AdapterResilience;
import pay.conflux.backend.adapters.support.HttpClientFactory;
import pay.conflux.backend.adapters.support.TokenService;
import pay.conflux.backend.adapters.support.VendorAuthClient;
import pay.conflux.backend.common.error.ErrorCode;
import pay.conflux.backend.common.money.Money;

/**
 * WireMock-driven contract suite for {@link BkashAdapter}. Single-vendor scope only; cross-adapter
 * isolation is exercised in the Wave C acceptance gate (sub-prompt 12).
 *
 * <p>Wave A's {@code VendorWireMockExtension} lives in {@code conflux-application} (downstream);
 * this suite in {@code conflux-adapters} registers {@link WireMockExtension} directly. Identical
 * behavior: one dynamic-port stub server per test class lifecycle.
 */
class BkashAdapterContractTest {

  @RegisterExtension
  static final WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static final String CREATE_PATH = "/tokenized/checkout/create";
  private static final String EXECUTE_PATH = "/tokenized/checkout/execute";
  private static final String STATUS_PATH = "/tokenized/checkout/payment/status";
  private static final String REFUND_PATH = "/tokenized/checkout/payment/refund";
  private static final String GRANT_PATH = "/tokenized/checkout/token/grant";

  private BkashAdapter adapter;
  private BkashAuthClient authClient;
  private CircuitBreakerRegistry registry;

  @BeforeEach
  void setUp() {
    HttpClientFactory factory = new HttpClientFactory();
    registry = CircuitBreakerRegistry.ofDefaults();
    CircuitBreaker breaker = registry.circuitBreaker(Vendor.BKASH.name());
    breaker.transitionToClosedState();
    AdapterResilience resilience = new AdapterResilience(registry);
    ObjectMapper objectMapper = new ObjectMapper();
    authClient = new BkashAuthClient(factory, objectMapper);
    TokenService tokenService = new InMemoryTokenService(authClient);
    adapter = new BkashAdapter(factory, tokenService, authClient, resilience, objectMapper);
  }

  private VendorCredentials creds() {
    return new VendorCredentials(
        Map.of(
            "appKey",
            "AK",
            "appSecret",
            "AS",
            "username",
            "u",
            "password",
            "p",
            "baseUrl",
            wm.baseUrl()));
  }

  private VendorPaymentRequest paymentRequest() {
    return new VendorPaymentRequest(
        UUID.randomUUID(),
        Money.of(new BigDecimal("100.00"), "BDT"),
        "ORDER-1",
        "https://merchant.example/callback",
        Map.of());
  }

  private void stubGrantToken(String token) {
    wm.stubFor(
        post(urlEqualTo(GRANT_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id_token\":\""
                            + token
                            + "\",\"expires_in\":3600,\"refresh_token\":\"r\","
                            + "\"token_type\":\"Bearer\"}")));
  }

  @Test
  void initiate_success_returnsRedirectUrl() {
    stubGrantToken("idtok-abcd-1234");
    wm.stubFor(
        post(urlEqualTo(CREATE_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"statusCode\":\"0000\",\"paymentID\":\"TRX12345678ABCD\","
                            + "\"bkashURL\":\"https://sandbox.bkash.com/pay/TRX12345678ABCD\"}")));

    VendorResponse res = adapter.initiate(paymentRequest(), creds());

    assertThat(res.status()).isEqualTo(VendorStatus.INITIATED);
    assertThat(res.vendorTrxId()).isEqualTo("TRX12345678ABCD");
    assertThat(res.redirectUrl()).isEqualTo("https://sandbox.bkash.com/pay/TRX12345678ABCD");
    assertThat(res.errorCode()).isNull();
    wm.verify(
        1,
        postRequestedFor(urlEqualTo(CREATE_PATH))
            .withHeader("Authorization", equalTo("idtok-abcd-1234"))
            .withHeader("X-APP-Key", equalTo("AK")));
  }

  @Test
  void initiate_insufficientFunds_mapsToInsufficientFunds() {
    stubGrantToken("t1");
    wm.stubFor(
        post(urlEqualTo(CREATE_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"statusCode\":\"2023\",\"statusMessage\":\"low balance\"}")));

    VendorResponse res = adapter.initiate(paymentRequest(), creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
  }

  @Test
  void initiate_duplicateTransaction_mapsToResourceAlreadyExists() {
    stubGrantToken("t1");
    wm.stubFor(
        post(urlEqualTo(CREATE_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"statusCode\":\"2056\",\"statusMessage\":\"duplicate\"}")));

    VendorResponse res = adapter.initiate(paymentRequest(), creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS);
  }

  @Test
  void initiate_401ThenSuccess_refreshesTokenExactlyOnce() {
    stubGrantToken("t-first");
    wm.stubFor(
        post(urlEqualTo(CREATE_PATH))
            .inScenario("auth-refresh")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(401))
            .willSetStateTo("refreshed"));
    wm.stubFor(
        post(urlEqualTo(CREATE_PATH))
            .inScenario("auth-refresh")
            .whenScenarioStateIs("refreshed")
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"statusCode\":\"0000\",\"paymentID\":\"PXY1234567890AB\","
                            + "\"bkashURL\":\"https://sandbox/x\"}")));

    VendorResponse res = adapter.initiate(paymentRequest(), creds());

    assertThat(res.status()).isEqualTo(VendorStatus.INITIATED);
    assertThat(res.vendorTrxId()).isEqualTo("PXY1234567890AB");
    wm.verify(2, postRequestedFor(urlEqualTo(CREATE_PATH)));
    wm.verify(2, postRequestedFor(urlEqualTo(GRANT_PATH)));
  }

  @Test
  void initiate_doubleAuthFailure_doesNotLoop() {
    stubGrantToken("t-stale");
    wm.stubFor(post(urlEqualTo(CREATE_PATH)).willReturn(aResponse().withStatus(401)));

    VendorResponse res = adapter.initiate(paymentRequest(), creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
    wm.verify(2, postRequestedFor(urlEqualTo(CREATE_PATH)));
  }

  @Test
  void initiate_timeout_mapsToVendorDown() {
    stubGrantToken("t1");
    wm.stubFor(
        post(urlEqualTo(CREATE_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withFixedDelay(11_000)
                    .withBody("{\"statusCode\":\"0000\"}")));

    long start = System.currentTimeMillis();
    VendorResponse res = adapter.initiate(paymentRequest(), creds());
    long elapsed = System.currentTimeMillis() - start;

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.VENDOR_DOWN);
    assertThat(elapsed).isLessThan(12_000);
  }

  @Test
  void confirm_success_capturesPayment() {
    stubGrantToken("t1");
    wm.stubFor(
        post(urlEqualTo(EXECUTE_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"statusCode\":\"0000\",\"paymentID\":\"PMT_X_ABCD\","
                            + "\"transactionStatus\":\"Completed\"}")));

    VendorResponse res = adapter.confirm("PMT_X_ABCD", creds());

    assertThat(res.status()).isEqualTo(VendorStatus.COMPLETED);
    assertThat(res.vendorTrxId()).isEqualTo("PMT_X_ABCD");
  }

  @Test
  void confirm_failure_mapsViaErrorMapper() {
    stubGrantToken("t1");
    wm.stubFor(
        post(urlEqualTo(EXECUTE_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"statusCode\":\"2003\",\"transactionStatus\":\"Failed\"}")));

    VendorResponse res = adapter.confirm("PMT_X_ABCD", creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  void queryStatus_completed_returnsCompleted() {
    stubGrantToken("t1");
    wm.stubFor(
        post(urlEqualTo(STATUS_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"statusCode\":\"0000\",\"transactionStatus\":\"Completed\"}")));

    VendorResponse res = adapter.queryStatus("PMT1", creds());

    assertThat(res.status()).isEqualTo(VendorStatus.COMPLETED);
  }

  @Test
  void queryStatus_pending_returnsInitiated() {
    stubGrantToken("t1");
    wm.stubFor(
        post(urlEqualTo(STATUS_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"statusCode\":\"0000\",\"transactionStatus\":\"Initiated\"}")));

    VendorResponse res = adapter.queryStatus("PMT1", creds());

    assertThat(res.status()).isEqualTo(VendorStatus.INITIATED);
  }

  @Test
  void queryStatus_cancelled_returnsCancelled() {
    stubGrantToken("t1");
    wm.stubFor(
        post(urlEqualTo(STATUS_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"statusCode\":\"0000\",\"transactionStatus\":\"Cancelled\"}")));

    VendorResponse res = adapter.queryStatus("PMT1", creds());

    assertThat(res.status()).isEqualTo(VendorStatus.CANCELLED);
  }

  @Test
  void refund_success() {
    stubGrantToken("t1");
    wm.stubFor(
        post(urlEqualTo(REFUND_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"statusCode\":\"0000\",\"transactionStatus\":\"Completed\","
                            + "\"refundTrxID\":\"RFND1234567890\"}")));

    VendorRefundRequest req =
        new VendorRefundRequest(
            UUID.randomUUID(),
            "ORIG_PMT_1",
            Money.of(new BigDecimal("25.00"), "BDT"),
            "customer requested");

    VendorResponse res = adapter.refund(req, creds());

    assertThat(res.status()).isEqualTo(VendorStatus.COMPLETED);
    assertThat(res.vendorTrxId()).isEqualTo("RFND1234567890");
  }

  @Test
  void refund_insufficient_refund_funds() {
    stubGrantToken("t1");
    wm.stubFor(
        post(urlEqualTo(REFUND_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"statusCode\":\"2023\",\"transactionStatus\":\"Failed\"}")));

    VendorRefundRequest req =
        new VendorRefundRequest(
            UUID.randomUUID(), "ORIG_PMT_1", Money.of(new BigDecimal("25.00"), "BDT"), "");

    VendorResponse res = adapter.refund(req, creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
  }

  @Test
  void confirm_blankPaymentId_throwsIllegalArgument() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.confirm("", creds()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("paymentID");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.confirm(null, creds()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void queryStatus_nullPaymentId_throwsIllegalArgument() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.queryStatus(null, creds()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void queryStatus_unknownTxStatus_failedWithMappedCode() {
    stubGrantToken("t1");
    wm.stubFor(
        post(urlEqualTo(STATUS_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"statusCode\":\"2003\",\"transactionStatus\":\"WeirdState\"}")));

    VendorResponse res = adapter.queryStatus("PMT_LONG_ID_123", creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  void initiate_missingCredential_throwsIllegalArgument() {
    VendorCredentials partial =
        new VendorCredentials(
            Map.of("appSecret", "AS", "username", "u", "password", "p", "baseUrl", wm.baseUrl()));
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> adapter.initiate(paymentRequest(), partial))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("appKey");
  }

  @Test
  void initiate_http500_returnsFailedAdapterFailure() {
    stubGrantToken("t1");
    wm.stubFor(post(urlEqualTo(CREATE_PATH)).willReturn(aResponse().withStatus(500)));

    VendorResponse res = adapter.initiate(paymentRequest(), creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
  }

  @Test
  void initiate_malformedJson_returnsFailedAdapterFailure() {
    stubGrantToken("t1");
    wm.stubFor(
        post(urlEqualTo(CREATE_PATH))
            .willReturn(aResponse().withHeader("Content-Type", "text/plain").withBody("nope")));

    VendorResponse res = adapter.initiate(paymentRequest(), creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
  }

  @Test
  void circuitBreakerOpen_failsClosedWithoutCallingVendor() {
    CircuitBreaker breaker = registry.circuitBreaker(Vendor.BKASH.name());
    breaker.transitionToOpenState();
    wm.stubFor(post(urlEqualTo(CREATE_PATH)).willReturn(aResponse().withStatus(200)));

    VendorResponse res = adapter.initiate(paymentRequest(), creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.VENDOR_DOWN);
    assertThat(res.rawResponse()).isEqualTo("circuit_open");
    wm.verify(0, postRequestedFor(urlEqualTo(CREATE_PATH)));
    breaker.transitionToClosedState();
  }

  /**
   * In-memory {@link TokenService} stand-in used to drive token-refresh behaviour
   * deterministically. Production uses {@code RedisTokenService}; that path is covered by the
   * integration test.
   */
  private static final class InMemoryTokenService implements TokenService {
    private final VendorAuthClient authClient;
    private final AtomicInteger calls = new AtomicInteger();
    private VendorAuthClient.AuthToken cached;

    InMemoryTokenService(VendorAuthClient authClient) {
      this.authClient = authClient;
    }

    @Override
    public String getToken(Vendor vendor, VendorCredentials creds) {
      calls.incrementAndGet();
      if (cached == null || Instant.now().isAfter(cached.expiresAt())) {
        cached = authClient.authenticate(vendor, creds);
      }
      return cached.token();
    }
  }
}
