package pay.conflux.backend.adapters.bkash;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pay.conflux.backend.adapters.error.MfsAdapterException;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;
import pay.conflux.backend.adapters.support.HttpClientFactory;
import pay.conflux.backend.adapters.support.VendorAuthClient;
import pay.conflux.backend.common.error.ErrorCode;

/**
 * Implementation of {@link VendorAuthClient} for bKash Tokenized Checkout v1.2 {@code grant_token}
 * flow.
 *
 * <p>POSTs JSON {@code {"app_key":..., "app_secret":...}} to {@code
 * {baseUrl}/tokenized/checkout/token/grant} with the {@code username} / {@code password} pair
 * delivered via headers (per bKash spec — NOT as an {@code Authorization} header). Parses the JSON
 * envelope {@code { id_token, expires_in, refresh_token, token_type }} and returns an {@link
 * AuthToken} whose expiry is {@code now + expires_in - 60s} (60-second safety margin so a cached
 * token never expires mid-call).
 *
 * <p>Required {@link VendorCredentials} keys (camelCase, per Spring relaxed binding from {@code
 * application.yml}):
 *
 * <ul>
 *   <li>{@code appKey}
 *   <li>{@code appSecret}
 *   <li>{@code username}
 *   <li>{@code password}
 *   <li>{@code baseUrl}
 * </ul>
 *
 * <p><b>Bean registration deviation:</b> declared {@link Primary} so that {@code RedisTokenService}
 * autowires this bean rather than {@code MockVendorAuthClient}. The mock client throws on every
 * non-{@code MOCK} vendor and would otherwise win in production. Wave A's mock-vendor integration
 * test ({@code MockTokenRefreshIT}) overrides this primary with its own {@code @TestConfiguration},
 * so existing test coverage is preserved. A follow-up wave should replace this with an explicit
 * vendor-keyed router in {@code support/}.
 */
@Component
@Primary
public class BkashAuthClient implements VendorAuthClient {

  private static final Logger log = LoggerFactory.getLogger(BkashAuthClient.class);

  private static final String GRANT_TOKEN_PATH = "/tokenized/checkout/token/grant";
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  private static final int SAFETY_MARGIN_SECONDS = 60;

  private static final String KEY_APP_KEY = "appKey";
  private static final String KEY_APP_SECRET = "appSecret";
  private static final String KEY_USERNAME = "username";
  private static final String KEY_PASSWORD = "password";
  private static final String KEY_BASE_URL = "baseUrl";

  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;

  public BkashAuthClient(HttpClientFactory httpClientFactory, ObjectMapper objectMapper) {
    this.httpClient = httpClientFactory.clientFor(Vendor.BKASH);
    this.objectMapper = objectMapper;
  }

  @Override
  public AuthToken authenticate(Vendor v, VendorCredentials creds) {
    if (v != Vendor.BKASH) {
      throw new IllegalArgumentException("BkashAuthClient only supports BKASH; got: " + v);
    }
    String appKey = require(creds, KEY_APP_KEY);
    String appSecret = require(creds, KEY_APP_SECRET);
    String username = require(creds, KEY_USERNAME);
    String password = require(creds, KEY_PASSWORD);
    String baseUrl = require(creds, KEY_BASE_URL);

    String payload;
    try {
      payload =
          objectMapper.writeValueAsString(
              java.util.Map.of("app_key", appKey, "app_secret", appSecret));
    } catch (JsonProcessingException e) {
      throw new MfsAdapterException(
          ErrorCode.MFS_ADAPTER_FAILURE, "bkash auth: failed to serialize grant_token request", e);
    }

    Request request =
        new Request.Builder()
            .url(baseUrl + GRANT_TOKEN_PATH)
            .header("username", username)
            .header("password", password)
            .header("Accept", "application/json")
            .post(RequestBody.create(payload, JSON))
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      ResponseBody body = response.body();
      String raw = body == null ? "" : body.string();
      if (!response.isSuccessful()) {
        log.warn("BKASH auth http {} from {}", response.code(), GRANT_TOKEN_PATH);
        throw new MfsAdapterException(
            ErrorCode.MFS_ADAPTER_FAILURE, "bkash auth http " + response.code());
      }
      JsonNode root = objectMapper.readTree(raw);
      String idToken = textOrNull(root.get("id_token"));
      JsonNode expiresInNode = root.get("expires_in");
      if (idToken == null || expiresInNode == null || !expiresInNode.canConvertToLong()) {
        throw new MfsAdapterException(
            ErrorCode.MFS_ADAPTER_FAILURE, "bkash auth: malformed grant_token response");
      }
      long expiresIn = expiresInNode.asLong();
      Instant expiresAt =
          Instant.now().plusSeconds(Math.max(1L, expiresIn - SAFETY_MARGIN_SECONDS));
      log.info("BKASH auth ok tokenPrefix={} expiresInSec={}", mask(idToken), expiresIn);
      return new AuthToken(idToken, expiresAt);
    } catch (SocketTimeoutException e) {
      throw new MfsAdapterException(ErrorCode.MFS_ADAPTER_FAILURE, "bkash auth timeout", e);
    } catch (JsonProcessingException e) {
      throw new MfsAdapterException(
          ErrorCode.MFS_ADAPTER_FAILURE, "bkash auth: malformed json response", e);
    } catch (IOException e) {
      throw new MfsAdapterException(
          ErrorCode.MFS_ADAPTER_FAILURE, "bkash auth io: " + e.getClass().getSimpleName(), e);
    }
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
    if (value == null || value.length() <= 4) {
      return "***";
    }
    return value.substring(0, 4) + "***";
  }
}
