package pay.conflux.backend.adapters.bkash;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pay.conflux.backend.adapters.error.MfsAdapterException;
import pay.conflux.backend.adapters.port.PaymentProvider;
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

/**
 * bKash Tokenized Checkout v1.2 adapter.
 *
 * <p>Required {@link VendorCredentials} keys (camelCase, per Spring relaxed binding):
 *
 * <ul>
 *   <li>{@code appKey}, {@code appSecret} — issued by bKash merchant onboarding
 *   <li>{@code username}, {@code password} — sent as HTTP headers to {@code grant_token}
 *   <li>{@code baseUrl} — sandbox / production gateway base URL
 * </ul>
 *
 * <p><b>Token lifecycle.</b> Tokens are acquired via {@link TokenService}, which is Redis-backed
 * and shared across pods (keyed by {@code vendor} + credentials hash). On a {@code 401} from any
 * downstream call the adapter refreshes once via {@link BkashAuthClient#authenticate} and retries
 * exactly once; a second {@code 401} returns {@code FAILED} + {@link
 * ErrorCode#MFS_ADAPTER_FAILURE}. The retry path bypasses the cache because the shared {@link
 * TokenService} contract does not expose an {@code invalidate(...)} hook in Wave A; the fresh token
 * used in the retry is therefore not propagated back to Redis. A follow-up wave should add {@code
 * invalidate(...)} so the next request out of any pod sees the same fresh token.
 *
 * <p><b>FOLLOW-UP:</b> payment-core callback must invoke {@code BkashAdapter.confirm(...)} on Bkash
 * redirect; see Wave C report for tracker.
 *
 * <p><b>Logging discipline.</b> Adapter never logs {@code creds}, {@code id_token}, or full request
 * / response bodies. Session / payment IDs are masked as {@code <first4>***<last4>}.
 */
@Component
public class BkashAdapter implements PaymentProvider {

  private static final Logger log = LoggerFactory.getLogger(BkashAdapter.class);

  private static final String CREATE_PATH = "/tokenized/checkout/create";
  private static final String EXECUTE_PATH = "/tokenized/checkout/execute";
  private static final String STATUS_PATH = "/tokenized/checkout/payment/status";
  private static final String REFUND_PATH = "/tokenized/checkout/payment/refund";

  private static final String KEY_APP_KEY = "appKey";
  private static final String KEY_BASE_URL = "baseUrl";

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final OkHttpClient httpClient;
  private final TokenService tokenService;
  private final VendorAuthClient vendorAuthClient;
  private final AdapterResilience adapterResilience;
  private final ObjectMapper objectMapper;

  public BkashAdapter(
      HttpClientFactory httpClientFactory,
      TokenService tokenService,
      VendorAuthClient vendorAuthClient,
      AdapterResilience adapterResilience,
      ObjectMapper objectMapper) {
    this.httpClient = httpClientFactory.clientFor(Vendor.BKASH);
    this.tokenService = tokenService;
    this.vendorAuthClient = vendorAuthClient;
    this.adapterResilience = adapterResilience;
    this.objectMapper = objectMapper;
  }

  @Override
  public VendorResponse initiate(VendorPaymentRequest request, VendorCredentials creds) {
    String appKey = require(creds, KEY_APP_KEY);
    String baseUrl = require(creds, KEY_BASE_URL);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("mode", "0011");
    body.put("payerReference", " ");
    body.put("callbackURL", request.callbackUrl());
    body.put("amount", request.amount().amount().toPlainString());
    body.put("currency", "BDT");
    body.put("intent", "sale");
    body.put("merchantInvoiceNumber", request.transactionId().toString());

    try {
      return adapterResilience.executeWithCircuitBreaker(
          Vendor.BKASH, () -> doInitiate(baseUrl, appKey, creds, body, request.transactionId()));
    } catch (MfsAdapterException e) {
      return failedFromException(e);
    }
  }

  /**
   * Tokenized Checkout v1.2 Execute step. Public adapter-specific surface (NOT on {@link
   * PaymentProvider} port): captures the payment after the customer authorizes at the {@code
   * bkashURL}.
   *
   * @param paymentID vendor {@code paymentID} returned by a prior {@link #initiate} call
   * @param creds short-lived decrypted vendor credentials
   * @return unified {@link VendorResponse}; on success {@link VendorStatus#COMPLETED} and {@link
   *     VendorResponse#vendorTrxId()} equals {@code paymentID}
   */
  public VendorResponse confirm(String paymentID, VendorCredentials creds) {
    if (paymentID == null || paymentID.isBlank()) {
      throw new IllegalArgumentException("paymentID must not be null or blank");
    }
    String appKey = require(creds, KEY_APP_KEY);
    String baseUrl = require(creds, KEY_BASE_URL);

    try {
      return adapterResilience.executeWithCircuitBreaker(
          Vendor.BKASH, () -> doConfirm(baseUrl, appKey, creds, paymentID));
    } catch (MfsAdapterException e) {
      return failedFromException(e);
    }
  }

  @Override
  public VendorResponse queryStatus(String vendorTrxId, VendorCredentials creds) {
    if (vendorTrxId == null) {
      throw new IllegalArgumentException("vendorTrxId must not be null");
    }
    String appKey = require(creds, KEY_APP_KEY);
    String baseUrl = require(creds, KEY_BASE_URL);

    try {
      return adapterResilience.executeWithCircuitBreaker(
          Vendor.BKASH, () -> doQueryStatus(baseUrl, appKey, creds, vendorTrxId));
    } catch (MfsAdapterException e) {
      return failedFromException(e);
    }
  }

  @Override
  public VendorResponse refund(VendorRefundRequest request, VendorCredentials creds) {
    String appKey = require(creds, KEY_APP_KEY);
    String baseUrl = require(creds, KEY_BASE_URL);
    String reason = request.reason() == null ? "" : request.reason();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("paymentID", request.originalVendorTrxId());
    body.put("amount", request.amount().amount().toPlainString());
    body.put("trxID", request.originalVendorTrxId());
    body.put("sku", reason);
    body.put("reason", reason);

    try {
      return adapterResilience.executeWithCircuitBreaker(
          Vendor.BKASH, () -> doRefund(baseUrl, appKey, creds, body, request.transactionId()));
    } catch (MfsAdapterException e) {
      return failedFromException(e);
    }
  }

  @Override
  public boolean supports(Vendor vendor) {
    return vendor == Vendor.BKASH;
  }

  private VendorResponse doInitiate(
      String baseUrl,
      String appKey,
      VendorCredentials creds,
      Map<String, Object> jsonBody,
      java.util.UUID businessTrx) {
    String responseBody = postWithRefresh(baseUrl + CREATE_PATH, appKey, creds, jsonBody);
    JsonNode root = parseJson(responseBody);
    String statusCode = textOrNull(root.get("statusCode"));
    if ("0000".equals(statusCode)) {
      String paymentID = textOrNull(root.get("paymentID"));
      String redirect = textOrNull(root.get("bkashURL"));
      log.info(
          "BKASH initiate businessTrx={} vendorPaymentID={} status=INITIATED",
          businessTrx,
          mask(paymentID));
      return new VendorResponse(VendorStatus.INITIATED, paymentID, redirect, responseBody, null);
    }
    ErrorCode code = BkashErrorMapper.mapCode(statusCode);
    log.info(
        "BKASH initiate businessTrx={} status=FAILED statusCode={} errorCode={}",
        businessTrx,
        statusCode,
        code);
    return new VendorResponse(VendorStatus.FAILED, null, null, responseBody, code);
  }

  private VendorResponse doConfirm(
      String baseUrl, String appKey, VendorCredentials creds, String paymentID) {
    Map<String, Object> body = Map.of("paymentID", paymentID);
    String responseBody = postWithRefresh(baseUrl + EXECUTE_PATH, appKey, creds, body);
    JsonNode root = parseJson(responseBody);
    String statusCode = textOrNull(root.get("statusCode"));
    String txStatus = textOrNull(root.get("transactionStatus"));
    if ("0000".equals(statusCode) && "Completed".equals(txStatus)) {
      log.info(
          "BKASH confirm vendorPaymentID={} status=COMPLETED txStatus={}",
          mask(paymentID),
          txStatus);
      return new VendorResponse(VendorStatus.COMPLETED, paymentID, null, responseBody, null);
    }
    ErrorCode code = BkashErrorMapper.mapCode(statusCode);
    log.info(
        "BKASH confirm vendorPaymentID={} status=FAILED statusCode={} errorCode={}",
        mask(paymentID),
        statusCode,
        code);
    return new VendorResponse(VendorStatus.FAILED, paymentID, null, responseBody, code);
  }

  private VendorResponse doQueryStatus(
      String baseUrl, String appKey, VendorCredentials creds, String paymentID) {
    Map<String, Object> body = Map.of("paymentID", paymentID);
    String responseBody = postWithRefresh(baseUrl + STATUS_PATH, appKey, creds, body);
    JsonNode root = parseJson(responseBody);
    String statusCode = textOrNull(root.get("statusCode"));
    String txStatus = textOrNull(root.get("transactionStatus"));
    log.info(
        "BKASH queryStatus vendorPaymentID={} statusCode={} txStatus={}",
        mask(paymentID),
        statusCode,
        txStatus);
    return switch (txStatus == null ? "" : txStatus) {
      case "Completed" ->
          new VendorResponse(VendorStatus.COMPLETED, paymentID, null, responseBody, null);
      case "Initiated", "Authorized" ->
          new VendorResponse(VendorStatus.INITIATED, paymentID, null, responseBody, null);
      case "Cancelled" ->
          new VendorResponse(VendorStatus.CANCELLED, paymentID, null, responseBody, null);
      default ->
          new VendorResponse(
              VendorStatus.FAILED,
              paymentID,
              null,
              responseBody,
              BkashErrorMapper.mapCode(statusCode));
    };
  }

  private VendorResponse doRefund(
      String baseUrl,
      String appKey,
      VendorCredentials creds,
      Map<String, Object> jsonBody,
      java.util.UUID businessTrx) {
    String responseBody = postWithRefresh(baseUrl + REFUND_PATH, appKey, creds, jsonBody);
    JsonNode root = parseJson(responseBody);
    String statusCode = textOrNull(root.get("statusCode"));
    String txStatus = textOrNull(root.get("transactionStatus"));
    String refundTrxID = textOrNull(root.get("refundTrxID"));
    if ("0000".equals(statusCode) && "Completed".equals(txStatus)) {
      log.info(
          "BKASH refund businessTrx={} refundTrx={} status=COMPLETED",
          businessTrx,
          mask(refundTrxID));
      return new VendorResponse(VendorStatus.COMPLETED, refundTrxID, null, responseBody, null);
    }
    ErrorCode code = BkashErrorMapper.mapCode(statusCode);
    log.info(
        "BKASH refund businessTrx={} status=FAILED statusCode={} errorCode={}",
        businessTrx,
        statusCode,
        code);
    return new VendorResponse(VendorStatus.FAILED, refundTrxID, null, responseBody, code);
  }

  /**
   * POSTs the given JSON body with {@code Authorization=<id_token>}. On HTTP 401 refreshes the
   * token via {@link BkashAuthClient#authenticate} (bypassing the Redis cache because {@link
   * TokenService} has no {@code invalidate(...)} contract in Wave A) and retries exactly once.
   */
  private String postWithRefresh(
      String url, String appKey, VendorCredentials creds, Map<String, Object> body) {
    String token = tokenService.getToken(Vendor.BKASH, creds);
    String serialized = toJson(body);

    HttpResult first = doPostOnce(url, appKey, token, serialized);
    if (first.code() != 401) {
      return checkAndReturn(url, first);
    }
    log.info("BKASH 401 on {} — refreshing token once", pathOf(url));
    VendorAuthClient.AuthToken fresh = vendorAuthClient.authenticate(Vendor.BKASH, creds);
    HttpResult second = doPostOnce(url, appKey, fresh.token(), serialized);
    if (second.code() == 401) {
      throw new MfsAdapterException(
          ErrorCode.MFS_ADAPTER_FAILURE, "bkash double 401 on " + pathOf(url));
    }
    return checkAndReturn(url, second);
  }

  private String checkAndReturn(String url, HttpResult r) {
    if (r.code() >= 200 && r.code() < 300) {
      return r.body();
    }
    log.warn("BKASH http {} from {}", r.code(), pathOf(url));
    throw new MfsAdapterException(ErrorCode.MFS_ADAPTER_FAILURE, "bkash http " + r.code());
  }

  private HttpResult doPostOnce(String url, String appKey, String token, String serialized) {
    Request request =
        new Request.Builder()
            .url(url)
            .header("Authorization", token)
            .header("X-APP-Key", appKey)
            .header("Accept", "application/json")
            .post(RequestBody.create(serialized, JSON))
            .build();
    try (Response response = httpClient.newCall(request).execute()) {
      ResponseBody rb = response.body();
      String raw = rb == null ? "" : rb.string();
      return new HttpResult(response.code(), raw);
    } catch (SocketTimeoutException e) {
      throw new MfsAdapterException(ErrorCode.VENDOR_DOWN, "bkash timeout " + pathOf(url), e);
    } catch (IOException e) {
      throw new MfsAdapterException(
          ErrorCode.MFS_ADAPTER_FAILURE, "bkash io: " + e.getClass().getSimpleName(), e);
    }
  }

  private String toJson(Map<String, Object> body) {
    try {
      return objectMapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new MfsAdapterException(ErrorCode.MFS_ADAPTER_FAILURE, "bkash serialize body", e);
    }
  }

  private JsonNode parseJson(String body) {
    try {
      return objectMapper.readTree(body);
    } catch (JsonProcessingException e) {
      throw new MfsAdapterException(ErrorCode.MFS_ADAPTER_FAILURE, "bkash malformed json", e);
    }
  }

  private static VendorResponse failedFromException(MfsAdapterException e) {
    String raw =
        e.getMessage() != null && e.getMessage().contains("Circuit open") ? "circuit_open" : null;
    return new VendorResponse(VendorStatus.FAILED, null, null, raw, e.getErrorCode());
  }

  private static String require(VendorCredentials creds, String key) {
    String value = creds.values().get(key);
    if (value == null) {
      throw new IllegalArgumentException("bkash credentials missing key: " + key);
    }
    return value;
  }

  private static String textOrNull(JsonNode node) {
    return node == null || node.isNull() ? null : node.asText();
  }

  private static String mask(String value) {
    if (value == null) {
      return null;
    }
    if (value.length() <= 8) {
      return "***";
    }
    return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
  }

  private static String pathOf(String url) {
    int idx = url.indexOf("/tokenized");
    return idx >= 0 ? url.substring(idx) : url;
  }

  /** Inner holder so {@link #postWithRefresh} can return both HTTP status and body cheaply. */
  private record HttpResult(int code, String body) {}
}
