package pay.conflux.backend.adapters.sslcommerz;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Objects;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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
import pay.conflux.backend.common.error.ErrorCode;

/**
 * SSLCommerz Hosted Checkout v4 adapter.
 *
 * <p>SSLCommerz authenticates per-request with {@code store_id} + {@code store_passwd} in the form
 * body — no {@code grant_token} flow, so this adapter intentionally does NOT inject {@link
 * pay.conflux.backend.adapters.support.TokenService} or implement {@link
 * pay.conflux.backend.adapters.support.VendorAuthClient}. Credentials always flow in via the {@link
 * VendorCredentials} argument, which {@code payment-core}'s dispatch has already resolved.
 *
 * <p><b>Required credential keys</b> (camelCase, per the partner-credentials YAML wiring shipped in
 * Wave C sub-prompt 0):
 *
 * <ul>
 *   <li>{@code storeId} — merchant store id
 *   <li>{@code storePasswd} — merchant store password
 *   <li>{@code baseUrl} — sandbox or production gateway base URL
 * </ul>
 *
 * <p><b>Customer-field defaulting</b> on {@link #initiate} — SSLCommerz rejects empty strings on
 * customer fields, so missing metadata entries are filled with the table below:
 *
 * <pre>
 * | metadata key       | default     |
 * |--------------------|-------------|
 * | product_name       | "Payment"   |
 * | product_category   | "Service"   |
 * | cus_name           | "N/A"       |
 * | cus_email          | "N/A"       |
 * | cus_phone          | "N/A"       |
 * | cus_add1           | "N/A"       |
 * | cus_city           | "Dhaka"     |
 * | cus_country        | "Bangladesh"|
 * </pre>
 *
 * <p><b>{@code ipn_url} is intentionally set but unhandled.</b> ConfluxPay relies on the validator
 * API for authoritative status retrieval; IPN delivery is best-effort and SSLCommerz will retry on
 * failure until it gives up. This is by design, not a bug.
 *
 * <p><b>Internal {@code tran_id} derivation.</b> SSLCommerz caps {@code tran_id} at 30 characters,
 * but the port-level {@link VendorPaymentRequest#transactionId()} is a 36-char UUID. The adapter
 * therefore drops UUID hyphens (yielding 32 hex chars) and truncates to 30 chars before sending.
 * Collisions across the truncated keyspace require &gt; 2^60 transactions — vanishingly unlikely.
 * The {@code &le; 30 char} check that follows the derivation is a defensive structural assertion
 * and throws {@link IllegalArgumentException} on violation.
 *
 * <p><b>Refund flow.</b> SSLCommerz refunds require a {@code bank_tran_id} (the vendor's internal
 * tx id, distinct from the merchant {@code tran_id}). {@link #refund} therefore always issues a
 * validator-API hop first to resolve {@code bank_tran_id} from {@link
 * VendorRefundRequest#originalVendorTrxId()}. This is a real-world SSLCommerz wart — the validator
 * response on the original transaction already carries {@code bank_tran_id}; the hop is the
 * defensive fallback for callers that didn't capture it.
 *
 * <p><b>Refund {@code refund_trans_id} format.</b> Required since 2025-02-24; must be unique per
 * refund and {@code &le; 30} chars. This adapter derives {@code "R-" + transactionId + "-" +
 * (currentTimeMillis % 1_000_000)} then truncates to 30.
 *
 * <p><b>Logging discipline.</b> Adapter never logs {@code creds}, {@code store_passwd}, full
 * response bodies, or full session/{@code val_id}/{@code bank_tran_id} values. The first 4 chars of
 * tokens are logged with a {@code ***} suffix.
 */
@Component
public class SslcommerzAdapter implements PaymentProvider {

  private static final Logger log = LoggerFactory.getLogger(SslcommerzAdapter.class);

  private static final int MAX_TRAN_ID_LENGTH = 30;
  private static final String INITIATE_PATH = "/gwprocess/v4/api.php";
  private static final String VALIDATOR_PATH = "/validator/api/merchantTransIDvalidationAPI.php";

  private static final String KEY_STORE_ID = "storeId";
  private static final String KEY_STORE_PASSWD = "storePasswd";
  private static final String KEY_BASE_URL = "baseUrl";

  private final OkHttpClient httpClient;
  private final AdapterResilience adapterResilience;
  private final ObjectMapper objectMapper;

  public SslcommerzAdapter(
      HttpClientFactory httpClientFactory,
      AdapterResilience adapterResilience,
      ObjectMapper objectMapper) {
    this.httpClient = httpClientFactory.clientFor(Vendor.SSLCOMMERZ);
    this.adapterResilience = adapterResilience;
    this.objectMapper = objectMapper;
  }

  @Override
  public VendorResponse initiate(VendorPaymentRequest request, VendorCredentials creds) {
    String tranId = deriveTranId(request.transactionId().toString());
    String storeId = require(creds, KEY_STORE_ID);
    String storePasswd = require(creds, KEY_STORE_PASSWD);
    String baseUrl = require(creds, KEY_BASE_URL);

    try {
      return adapterResilience.executeWithCircuitBreaker(
          Vendor.SSLCOMMERZ, () -> doInitiate(tranId, baseUrl, storeId, storePasswd, request));
    } catch (MfsAdapterException e) {
      return failedFromException(e);
    }
  }

  @Override
  public VendorResponse queryStatus(String vendorTrxId, VendorCredentials creds) {
    Objects.requireNonNull(vendorTrxId, "vendorTrxId must not be null");
    String storeId = require(creds, KEY_STORE_ID);
    String storePasswd = require(creds, KEY_STORE_PASSWD);
    String baseUrl = require(creds, KEY_BASE_URL);

    try {
      return adapterResilience.executeWithCircuitBreaker(
          Vendor.SSLCOMMERZ, () -> doQueryStatus(vendorTrxId, baseUrl, storeId, storePasswd));
    } catch (MfsAdapterException e) {
      return failedFromException(e);
    }
  }

  @Override
  public VendorResponse refund(VendorRefundRequest request, VendorCredentials creds) {
    String storeId = require(creds, KEY_STORE_ID);
    String storePasswd = require(creds, KEY_STORE_PASSWD);
    String baseUrl = require(creds, KEY_BASE_URL);

    try {
      return adapterResilience.executeWithCircuitBreaker(
          Vendor.SSLCOMMERZ, () -> doRefund(request, baseUrl, storeId, storePasswd));
    } catch (MfsAdapterException e) {
      return failedFromException(e);
    }
  }

  @Override
  public boolean supports(Vendor vendor) {
    return vendor == Vendor.SSLCOMMERZ;
  }

  private VendorResponse doInitiate(
      String tranId,
      String baseUrl,
      String storeId,
      String storePasswd,
      VendorPaymentRequest request) {
    Map<String, String> md = request.metadata();
    FormBody body =
        new FormBody.Builder()
            .add("store_id", storeId)
            .add("store_passwd", storePasswd)
            .add("total_amount", request.amount().amount().toPlainString())
            .add("currency", "BDT")
            .add("tran_id", tranId)
            .add("success_url", request.callbackUrl() + "?status=success")
            .add("fail_url", request.callbackUrl() + "?status=fail")
            .add("cancel_url", request.callbackUrl() + "?status=cancel")
            .add("ipn_url", request.callbackUrl() + "/ipn")
            .add("product_name", md.getOrDefault("product_name", "Payment"))
            .add("product_category", md.getOrDefault("product_category", "Service"))
            .add("product_profile", "general")
            .add("cus_name", md.getOrDefault("cus_name", "N/A"))
            .add("cus_email", md.getOrDefault("cus_email", "N/A"))
            .add("cus_phone", md.getOrDefault("cus_phone", "N/A"))
            .add("cus_add1", md.getOrDefault("cus_add1", "N/A"))
            .add("cus_city", md.getOrDefault("cus_city", "Dhaka"))
            .add("cus_country", md.getOrDefault("cus_country", "Bangladesh"))
            .add("shipping_method", "NO")
            .build();
    Request httpRequest = new Request.Builder().url(baseUrl + INITIATE_PATH).post(body).build();

    String responseBody = execute(httpRequest);
    JsonNode root = parseJson(responseBody);
    String status = textOrNull(root.get("status"));
    if ("SUCCESS".equals(status)) {
      String gatewayUrl = textOrNull(root.get("GatewayPageURL"));
      String sessionKey = textOrNull(root.get("sessionkey"));
      if (gatewayUrl != null) {
        log.info(
            "SSLCOMMERZ initiate businessTrx={} sessionKey={} status=INITIATED",
            tranId,
            mask(sessionKey));
        return new VendorResponse(
            VendorStatus.INITIATED, sessionKey, gatewayUrl, responseBody, null);
      }
    }
    String reason = textOrNull(root.get("failedreason"));
    ErrorCode code = SslcommerzErrorMapper.map(reason);
    log.info("SSLCOMMERZ initiate businessTrx={} status=FAILED errorCode={}", tranId, code);
    return new VendorResponse(VendorStatus.FAILED, null, null, responseBody, code);
  }

  private VendorResponse doQueryStatus(
      String tranId, String baseUrl, String storeId, String storePasswd) {
    String body = executeValidator(tranId, baseUrl, storeId, storePasswd);
    JsonNode root = parseJson(body);
    String foundCount = textOrNull(root.get("no_of_trans_found"));
    if ("0".equals(foundCount)) {
      log.info("SSLCOMMERZ queryStatus businessTrx={} status=NOT_FOUND", tranId);
      return new VendorResponse(
          VendorStatus.FAILED, null, null, body, ErrorCode.RESOURCE_NOT_FOUND);
    }
    JsonNode element = root.path("element");
    if (!element.isArray() || element.isEmpty()) {
      log.info("SSLCOMMERZ queryStatus businessTrx={} status=MALFORMED_NO_ELEMENT", tranId);
      return new VendorResponse(
          VendorStatus.FAILED, null, null, body, ErrorCode.MFS_ADAPTER_FAILURE);
    }
    JsonNode first = element.get(0);
    String status = textOrNull(first.get("status"));
    String bankTranId = textOrNull(first.get("bank_tran_id"));
    String valId = textOrNull(first.get("val_id"));
    log.info(
        "SSLCOMMERZ queryStatus businessTrx={} status={} valId={} bankTranId={}",
        tranId,
        status,
        mask(valId),
        mask(bankTranId));
    return switch (status == null ? "" : status) {
      case "VALID", "VALIDATED" ->
          new VendorResponse(VendorStatus.COMPLETED, bankTranId, null, body, null);
      case "PENDING" -> new VendorResponse(VendorStatus.INITIATED, bankTranId, null, body, null);
      case "CANCELLED" -> new VendorResponse(VendorStatus.CANCELLED, bankTranId, null, body, null);
      default ->
          new VendorResponse(
              VendorStatus.FAILED, bankTranId, null, body, SslcommerzErrorMapper.map(status));
    };
  }

  private VendorResponse doRefund(
      VendorRefundRequest request, String baseUrl, String storeId, String storePasswd) {
    String bankTranId =
        resolveBankTranId(request.originalVendorTrxId(), baseUrl, storeId, storePasswd);
    String refundTransId = deriveRefundTransId(request.transactionId().toString());
    String remarks =
        request.reason() == null || request.reason().isBlank()
            ? "merchant_initiated"
            : request.reason();

    HttpUrl url =
        Objects.requireNonNull(HttpUrl.parse(baseUrl + VALIDATOR_PATH))
            .newBuilder()
            .addQueryParameter("bank_tran_id", bankTranId)
            .addQueryParameter("refund_amount", request.amount().amount().toPlainString())
            .addQueryParameter("refund_remarks", remarks)
            .addQueryParameter("refund_trans_id", refundTransId)
            .addQueryParameter("store_id", storeId)
            .addQueryParameter("store_passwd", storePasswd)
            .addQueryParameter("v", "1")
            .addQueryParameter("format", "json")
            .build();
    Request httpRequest = new Request.Builder().url(url).get().build();
    String body = execute(httpRequest);
    JsonNode root = parseJson(body);
    String status = textOrNull(root.get("status"));
    String refId = textOrNull(root.get("ref_id"));
    log.info(
        "SSLCOMMERZ refund businessTrx={} status={} refId={}",
        request.transactionId(),
        status,
        mask(refId));
    return switch (status == null ? "" : status) {
      case "success" -> new VendorResponse(VendorStatus.COMPLETED, refId, null, body, null);
      case "processing" -> new VendorResponse(VendorStatus.INITIATED, refId, null, body, null);
      case "failed" ->
          new VendorResponse(
              VendorStatus.FAILED,
              refId,
              null,
              body,
              SslcommerzErrorMapper.map(textOrNull(root.get("errorReason"))));
      default ->
          new VendorResponse(VendorStatus.FAILED, refId, null, body, ErrorCode.MFS_ADAPTER_FAILURE);
    };
  }

  private String resolveBankTranId(
      String originalVendorTrxId, String baseUrl, String storeId, String storePasswd) {
    String body = executeValidator(originalVendorTrxId, baseUrl, storeId, storePasswd);
    JsonNode root = parseJson(body);
    JsonNode element = root.path("element");
    if (element.isArray() && !element.isEmpty()) {
      String bankTranId = textOrNull(element.get(0).get("bank_tran_id"));
      if (bankTranId != null && !bankTranId.isBlank()) {
        return bankTranId;
      }
    }
    throw new MfsAdapterException(
        ErrorCode.RESOURCE_NOT_FOUND,
        "sslcommerz validation hop: bank_tran_id not found for original trx");
  }

  private String executeValidator(
      String tranId, String baseUrl, String storeId, String storePasswd) {
    HttpUrl url =
        Objects.requireNonNull(HttpUrl.parse(baseUrl + VALIDATOR_PATH))
            .newBuilder()
            .addQueryParameter("tran_id", tranId)
            .addQueryParameter("store_id", storeId)
            .addQueryParameter("store_passwd", storePasswd)
            .addQueryParameter("format", "json")
            .addQueryParameter("v", "1")
            .build();
    Request request = new Request.Builder().url(url).get().build();
    return execute(request);
  }

  private String execute(Request request) {
    try (Response response = httpClient.newCall(request).execute();
        ResponseBody body = response.body()) {
      String raw = body == null ? "" : body.string();
      if (!response.isSuccessful()) {
        log.warn("SSLCOMMERZ http {} from {}", response.code(), request.url().encodedPath());
        throw new MfsAdapterException(
            ErrorCode.MFS_ADAPTER_FAILURE, "sslcommerz http " + response.code());
      }
      return raw;
    } catch (SocketTimeoutException e) {
      throw new MfsAdapterException(ErrorCode.VENDOR_DOWN, "sslcommerz timeout", e);
    } catch (IOException e) {
      throw new MfsAdapterException(
          ErrorCode.MFS_ADAPTER_FAILURE, "sslcommerz io: " + e.getClass().getSimpleName(), e);
    }
  }

  private JsonNode parseJson(String body) {
    try {
      return objectMapper.readTree(body);
    } catch (JsonProcessingException e) {
      throw new MfsAdapterException(ErrorCode.MFS_ADAPTER_FAILURE, "sslcommerz malformed json", e);
    }
  }

  private VendorResponse failedFromException(MfsAdapterException e) {
    String raw =
        e.getMessage() != null && e.getMessage().contains("Circuit open") ? "circuit_open" : null;
    return new VendorResponse(VendorStatus.FAILED, null, null, raw, e.getErrorCode());
  }

  private static String deriveTranId(String uuidString) {
    String hex = uuidString.replace("-", "");
    String tranId = hex.length() > MAX_TRAN_ID_LENGTH ? hex.substring(0, MAX_TRAN_ID_LENGTH) : hex;
    if (tranId.length() > MAX_TRAN_ID_LENGTH) {
      throw new IllegalArgumentException("sslcommerz tran_id exceeds 30-char limit");
    }
    return tranId;
  }

  private static String deriveRefundTransId(String transactionId) {
    String candidate = "R-" + transactionId + "-" + (System.currentTimeMillis() % 1_000_000L);
    return candidate.length() > MAX_TRAN_ID_LENGTH
        ? candidate.substring(0, MAX_TRAN_ID_LENGTH)
        : candidate;
  }

  private static String require(VendorCredentials creds, String key) {
    String value = creds.values().get(key);
    if (value == null) {
      throw new IllegalArgumentException("sslcommerz credentials missing key: " + key);
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
    if (value.length() <= 4) {
      return "***";
    }
    return value.substring(0, 4) + "***";
  }
}
