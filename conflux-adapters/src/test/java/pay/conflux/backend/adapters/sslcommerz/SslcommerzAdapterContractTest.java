package pay.conflux.backend.adapters.sslcommerz;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
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
import pay.conflux.backend.common.error.ErrorCode;
import pay.conflux.backend.common.money.Money;

/**
 * WireMock-driven contract suite for {@link SslcommerzAdapter}. Single-vendor scope only;
 * cross-adapter isolation is exercised in the Wave C acceptance gate (sub-prompt 12).
 *
 * <p>Wave A's planned {@code VendorWireMockExtension} was never shipped, so this suite registers a
 * standard {@link WireMockExtension} directly. Behaviour is identical: one dynamic-port stub server
 * per test class lifecycle.
 */
class SslcommerzAdapterContractTest {

  @RegisterExtension
  static final WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static final String INITIATE_PATH = "/gwprocess/v4/api.php";
  private static final String VALIDATOR_PATH = "/validator/api/merchantTransIDvalidationAPI.php";

  private SslcommerzAdapter adapter;
  private CircuitBreakerRegistry registry;

  @BeforeEach
  void setUp() {
    HttpClientFactory factory = new HttpClientFactory();
    registry = CircuitBreakerRegistry.ofDefaults();
    registry.circuitBreaker(Vendor.SSLCOMMERZ.name());
    AdapterResilience resilience = new AdapterResilience(registry);
    adapter = new SslcommerzAdapter(factory, resilience, new ObjectMapper());
  }

  private VendorCredentials creds() {
    return new VendorCredentials(
        Map.of("storeId", "test_store", "storePasswd", "test_passwd", "baseUrl", wm.baseUrl()));
  }

  private VendorPaymentRequest paymentRequest(Map<String, String> metadata) {
    return new VendorPaymentRequest(
        UUID.randomUUID(),
        Money.of(new BigDecimal("100.00"), "BDT"),
        "ORDER-1",
        "https://merchant.example/callback",
        metadata);
  }

  @Test
  void initiate_success_returnsGatewayPageURL() {
    wm.stubFor(
        post(urlEqualTo(INITIATE_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"status\":\"SUCCESS\",\"sessionkey\":\"abc1234567890\","
                            + "\"GatewayPageURL\":\"https://sandbox.sslcommerz.com/gw/pay/abc\"}")));

    VendorResponse res = adapter.initiate(paymentRequest(Map.of()), creds());

    assertThat(res.status()).isEqualTo(VendorStatus.INITIATED);
    assertThat(res.redirectUrl()).isEqualTo("https://sandbox.sslcommerz.com/gw/pay/abc");
    assertThat(res.vendorTrxId()).isEqualTo("abc1234567890");
    assertThat(res.errorCode()).isNull();
    wm.verify(
        1,
        postRequestedFor(urlEqualTo(INITIATE_PATH))
            .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
            .withRequestBody(new AnythingPattern()));
  }

  @Test
  void initiate_failed_mapsViaSubstring() {
    wm.stubFor(
        post(urlEqualTo(INITIATE_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"status\":\"FAILED\",\"failedreason\":\"INSUFFICIENT BALANCE\"}")));

    VendorResponse res = adapter.initiate(paymentRequest(Map.of()), creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
  }

  @Test
  void initiate_validationError() {
    wm.stubFor(
        post(urlEqualTo(INITIATE_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"FAILED\",\"failedreason\":\"Invalid Card Number\"}")));

    VendorResponse res = adapter.initiate(paymentRequest(Map.of()), creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  void initiate_timeout_mapsToVendorDown() {
    wm.stubFor(
        post(urlEqualTo(INITIATE_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withFixedDelay(11_000)
                    .withBody("{\"status\":\"SUCCESS\"}")));

    long start = System.currentTimeMillis();
    VendorResponse res = adapter.initiate(paymentRequest(Map.of()), creds());
    long elapsed = System.currentTimeMillis() - start;

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.VENDOR_DOWN);
    assertThat(elapsed).isLessThan(12_000);
  }

  @Test
  void initiate_malformedJson_mapsToAdapterFailure() {
    wm.stubFor(
        post(urlEqualTo(INITIATE_PATH))
            .willReturn(aResponse().withHeader("Content-Type", "text/plain").withBody("not json")));

    VendorResponse res = adapter.initiate(paymentRequest(Map.of()), creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
  }

  @Test
  void initiate_missingCredential_throwsIllegalArgument() {
    VendorCredentials partial =
        new VendorCredentials(Map.of("storeId", "s", "baseUrl", wm.baseUrl()));
    assertThatThrownBy(() -> adapter.initiate(paymentRequest(Map.of()), partial))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("storePasswd");
  }

  @Test
  void initiate_withRichMetadata_sendsCustomerFields() {
    wm.stubFor(
        post(urlEqualTo(INITIATE_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"status\":\"SUCCESS\","
                            + "\"sessionkey\":\"sess1234\","
                            + "\"GatewayPageURL\":\"https://sandbox/x\"}")));

    Map<String, String> meta =
        Map.of(
            "product_name", "Widget",
            "product_category", "Goods",
            "cus_name", "John Doe",
            "cus_email", "john@example.com",
            "cus_phone", "0171000000",
            "cus_add1", "123 Lane",
            "cus_city", "Chittagong",
            "cus_country", "Bangladesh");
    VendorResponse res = adapter.initiate(paymentRequest(meta), creds());

    assertThat(res.status()).isEqualTo(VendorStatus.INITIATED);
  }

  @Test
  void queryStatus_validated_returnsCompleted() {
    wm.stubFor(
        get(urlPathEqualTo(VALIDATOR_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"APIConnect\":\"DONE\",\"no_of_trans_found\":\"1\","
                            + "\"element\":[{\"status\":\"VALIDATED\","
                            + "\"bank_tran_id\":\"BNK99887766\","
                            + "\"val_id\":\"V123456789\"}]}")));

    VendorResponse res = adapter.queryStatus("TRX1", creds());

    assertThat(res.status()).isEqualTo(VendorStatus.COMPLETED);
    assertThat(res.vendorTrxId()).isEqualTo("BNK99887766");
  }

  @Test
  void queryStatus_pending_returnsInitiated() {
    wm.stubFor(
        get(urlPathEqualTo(VALIDATOR_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"APIConnect\":\"DONE\",\"no_of_trans_found\":\"1\","
                            + "\"element\":[{\"status\":\"PENDING\","
                            + "\"bank_tran_id\":\"BNK111\"}]}")));

    VendorResponse res = adapter.queryStatus("TRX1", creds());

    assertThat(res.status()).isEqualTo(VendorStatus.INITIATED);
  }

  @Test
  void queryStatus_cancelled_returnsCancelled() {
    wm.stubFor(
        get(urlPathEqualTo(VALIDATOR_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"APIConnect\":\"DONE\",\"no_of_trans_found\":\"1\","
                            + "\"element\":[{\"status\":\"CANCELLED\","
                            + "\"bank_tran_id\":\"BNK222\"}]}")));

    VendorResponse res = adapter.queryStatus("TRX1", creds());

    assertThat(res.status()).isEqualTo(VendorStatus.CANCELLED);
  }

  @Test
  void queryStatus_failedExpired_mapsViaMapper() {
    wm.stubFor(
        get(urlPathEqualTo(VALIDATOR_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"APIConnect\":\"DONE\",\"no_of_trans_found\":\"1\","
                            + "\"element\":[{\"status\":\"EXPIRED\"}]}")));

    VendorResponse res = adapter.queryStatus("TRX1", creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  void queryStatus_notFound_returnsResourceNotFound() {
    wm.stubFor(
        get(urlPathEqualTo(VALIDATOR_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"APIConnect\":\"DONE\",\"no_of_trans_found\":\"0\",\"element\":[]}")));

    VendorResponse res = adapter.queryStatus("MISSING", creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void refund_success() {
    wm.stubFor(
        get(urlPathEqualTo(VALIDATOR_PATH))
            .atPriority(2)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"APIConnect\":\"DONE\",\"no_of_trans_found\":\"1\","
                            + "\"element\":[{\"status\":\"VALIDATED\","
                            + "\"bank_tran_id\":\"BNK_ORIG_001\"}]}")));
    wm.stubFor(
        get(urlPathEqualTo(VALIDATOR_PATH))
            .atPriority(1)
            .withQueryParam("bank_tran_id", equalTo("BNK_ORIG_001"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"APIConnect\":\"DONE\",\"trans_id\":\"BNK_ORIG_001\","
                            + "\"ref_id\":\"REF12345\",\"status\":\"success\"}")));

    VendorRefundRequest req =
        new VendorRefundRequest(
            UUID.randomUUID(),
            "MERCH_TRX_1",
            Money.of(new BigDecimal("50.00"), "BDT"),
            "customer requested");
    VendorResponse res = adapter.refund(req, creds());

    assertThat(res.status()).isEqualTo(VendorStatus.COMPLETED);
    assertThat(res.vendorTrxId()).isEqualTo("REF12345");
  }

  @Test
  void refund_sessionKeyOnly_doesValidationHopFirst() {
    wm.stubFor(
        get(urlPathEqualTo(VALIDATOR_PATH))
            .atPriority(2)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"APIConnect\":\"DONE\",\"no_of_trans_found\":\"1\","
                            + "\"element\":[{\"status\":\"VALIDATED\","
                            + "\"bank_tran_id\":\"BNK_DISCOVERED\"}]}")));
    wm.stubFor(
        get(urlPathEqualTo(VALIDATOR_PATH))
            .atPriority(1)
            .withQueryParam("bank_tran_id", equalTo("BNK_DISCOVERED"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"APIConnect\":\"DONE\",\"ref_id\":\"REF_X\",\"status\":\"success\"}")));

    VendorRefundRequest req =
        new VendorRefundRequest(
            UUID.randomUUID(),
            "SESSIONKEY_ABCDEF1234",
            Money.of(new BigDecimal("25.00"), "BDT"),
            "");
    VendorResponse res = adapter.refund(req, creds());

    assertThat(res.status()).isEqualTo(VendorStatus.COMPLETED);
    wm.verify(
        1,
        getRequestedFor(urlPathEqualTo(VALIDATOR_PATH))
            .withQueryParam("tran_id", equalTo("SESSIONKEY_ABCDEF1234")));
    wm.verify(
        1,
        getRequestedFor(urlPathEqualTo(VALIDATOR_PATH))
            .withQueryParam("bank_tran_id", equalTo("BNK_DISCOVERED")));
  }

  @Test
  void refund_failed_mapsViaMapper() {
    wm.stubFor(
        get(urlPathEqualTo(VALIDATOR_PATH))
            .atPriority(2)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"APIConnect\":\"DONE\",\"no_of_trans_found\":\"1\","
                            + "\"element\":[{\"status\":\"VALIDATED\","
                            + "\"bank_tran_id\":\"BNK_R\"}]}")));
    wm.stubFor(
        get(urlPathEqualTo(VALIDATOR_PATH))
            .atPriority(1)
            .withQueryParam("bank_tran_id", equalTo("BNK_R"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"status\":\"failed\",\"errorReason\":\"Insufficient balance for"
                            + " refund\"}")));

    VendorRefundRequest req =
        new VendorRefundRequest(
            UUID.randomUUID(), "MTRX", Money.of(new BigDecimal("10.00"), "BDT"), "test");
    VendorResponse res = adapter.refund(req, creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
  }

  @Test
  void refund_processing_returnsInitiated() {
    wm.stubFor(
        get(urlPathEqualTo(VALIDATOR_PATH))
            .atPriority(2)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"APIConnect\":\"DONE\",\"no_of_trans_found\":\"1\","
                            + "\"element\":[{\"status\":\"VALIDATED\","
                            + "\"bank_tran_id\":\"BNK_P\"}]}")));
    wm.stubFor(
        get(urlPathEqualTo(VALIDATOR_PATH))
            .atPriority(1)
            .withQueryParam("bank_tran_id", equalTo("BNK_P"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"processing\",\"ref_id\":\"REF_P\"}")));

    VendorRefundRequest req =
        new VendorRefundRequest(
            UUID.randomUUID(), "MTRX", Money.of(new BigDecimal("10.00"), "BDT"), "");
    VendorResponse res = adapter.refund(req, creds());

    assertThat(res.status()).isEqualTo(VendorStatus.INITIATED);
  }

  @Test
  void refund_validationHopFindsNoBankTranId_returnsResourceNotFound() {
    wm.stubFor(
        get(urlPathEqualTo(VALIDATOR_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"APIConnect\":\"DONE\",\"no_of_trans_found\":\"0\",\"element\":[]}")));

    VendorRefundRequest req =
        new VendorRefundRequest(
            UUID.randomUUID(), "UNKNOWN_TRX", Money.of(new BigDecimal("10.00"), "BDT"), "test");
    VendorResponse res = adapter.refund(req, creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void circuitBreakerOpen_failsClosedWithoutCallingVendor() {
    CircuitBreaker breaker = registry.circuitBreaker(Vendor.SSLCOMMERZ.name());
    breaker.transitionToOpenState();
    wm.stubFor(post(urlEqualTo(INITIATE_PATH)).willReturn(aResponse().withStatus(200)));

    VendorResponse res = adapter.initiate(paymentRequest(Map.of()), creds());

    assertThat(res.status()).isEqualTo(VendorStatus.FAILED);
    assertThat(res.errorCode()).isEqualTo(ErrorCode.VENDOR_DOWN);
    assertThat(res.rawResponse()).isEqualTo("circuit_open");
    wm.verify(0, postRequestedFor(urlEqualTo(INITIATE_PATH)));
    breaker.transitionToClosedState();
  }
}
