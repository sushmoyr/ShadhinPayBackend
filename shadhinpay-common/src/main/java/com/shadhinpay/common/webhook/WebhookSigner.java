package com.shadhinpay.common.webhook;

import com.shadhinpay.common.crypto.HmacSigner;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Convenience wrapper around {@link HmacSigner} that produces and verifies the {@code
 * X-ShadhinPay-Signature} header value used for outbound webhook integrity. The signature is
 * HMAC-SHA256 over the raw JSON request body, hex-encoded, using the merchant's webhook secret.
 */
@Component
public class WebhookSigner {

  public static final String SIGNATURE_HEADER = "X-ShadhinPay-Signature";

  private final HmacSigner hmacSigner;

  public WebhookSigner(HmacSigner hmacSigner) {
    this.hmacSigner = hmacSigner;
  }

  /**
   * Compute the {@code X-ShadhinPay-Signature} header value for the given payload.
   *
   * @param jsonPayload raw JSON request body — must match the bytes the receiver will hash
   * @param secret merchant's {@code webhookSecret}
   * @return hex-encoded HMAC-SHA256 signature
   */
  public String signatureFor(String jsonPayload, String secret) {
    Objects.requireNonNull(jsonPayload, "jsonPayload must not be null");
    return hmacSigner.sign(jsonPayload.getBytes(StandardCharsets.UTF_8), secret);
  }

  /**
   * Verify a header value against the given payload and secret using a constant-time compare.
   *
   * @param jsonPayload the raw JSON body that was received
   * @param secret merchant's {@code webhookSecret}
   * @param headerValue the value of the {@code X-ShadhinPay-Signature} header
   */
  public boolean verify(String jsonPayload, String secret, String headerValue) {
    Objects.requireNonNull(jsonPayload, "jsonPayload must not be null");
    Objects.requireNonNull(headerValue, "headerValue must not be null");
    return hmacSigner.verify(jsonPayload.getBytes(StandardCharsets.UTF_8), secret, headerValue);
  }
}
