package com.shadhinpay.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacSigner {

  private static final String HMAC_SHA256 = "HmacSHA256";

  public String sign(byte[] payload, String secret) {
    Objects.requireNonNull(payload, "payload must not be null");
    Objects.requireNonNull(secret, "secret must not be null");
    if (secret.isEmpty()) {
      throw new IllegalArgumentException("secret must not be empty");
    }
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
      return HexFormat.of().formatHex(mac.doFinal(payload));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 signing failed", e);
    }
  }

  public boolean verify(byte[] payload, String secret, String signature) {
    Objects.requireNonNull(signature, "signature must not be null");
    String expected = sign(payload, secret);
    byte[] a = expected.getBytes(StandardCharsets.US_ASCII);
    byte[] b = signature.getBytes(StandardCharsets.US_ASCII);
    return MessageDigest.isEqual(a, b);
  }
}
