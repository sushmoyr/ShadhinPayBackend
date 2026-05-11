package pay.conflux.backend.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmCipher {

  static final int IV_BYTES = 12;
  static final int TAG_BITS = 128;
  static final int KEY_BYTES = 32;
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String AES = "AES";
  private static final String HMAC_SHA256 = "HmacSHA256";

  private final byte[] masterKey;
  private final SecureRandom random;

  public AesGcmCipher(byte[] masterKey) {
    Objects.requireNonNull(masterKey, "masterKey must not be null");
    if (masterKey.length != KEY_BYTES) {
      throw new IllegalArgumentException("masterKey must be 32 bytes (AES-256)");
    }
    this.masterKey = masterKey.clone();
    this.random = new SecureRandom();
  }

  public String encrypt(String plaintext, String purpose) {
    Objects.requireNonNull(plaintext, "plaintext must not be null");
    requirePurpose(purpose);
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      SecretKeySpec key = new SecretKeySpec(deriveKey(purpose), AES);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] ctAndTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[iv.length + ctAndTag.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(ctAndTag, 0, out, iv.length, ctAndTag.length);
      return Base64.getEncoder().encodeToString(out);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM encryption failed", e);
    }
  }

  public String decrypt(String ciphertext, String purpose) {
    Objects.requireNonNull(ciphertext, "ciphertext must not be null");
    requirePurpose(purpose);
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(ciphertext);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("ciphertext is not valid Base64", e);
    }
    if (decoded.length < IV_BYTES + TAG_BITS / 8) {
      throw new IllegalArgumentException("ciphertext too short");
    }
    try {
      byte[] iv = Arrays.copyOfRange(decoded, 0, IV_BYTES);
      byte[] ctAndTag = Arrays.copyOfRange(decoded, IV_BYTES, decoded.length);
      SecretKeySpec key = new SecretKeySpec(deriveKey(purpose), AES);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] plain = cipher.doFinal(ctAndTag);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM decryption failed", e);
    }
  }

  private byte[] deriveKey(String purpose) {
    byte[] info = purpose.getBytes(StandardCharsets.UTF_8);
    byte[] prk = hkdfExtract(new byte[32], masterKey);
    return hkdfExpand(prk, info, KEY_BYTES);
  }

  private static byte[] hkdfExtract(byte[] salt, byte[] ikm) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256);
      mac.init(new SecretKeySpec(salt, HMAC_SHA256));
      return mac.doFinal(ikm);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HKDF extract failed", e);
    }
  }

  private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256);
      mac.init(new SecretKeySpec(prk, HMAC_SHA256));
      int hashLen = mac.getMacLength();
      int n = (int) Math.ceil((double) length / hashLen);
      if (n > 255) {
        throw new IllegalArgumentException("HKDF length too large");
      }
      byte[] okm = new byte[length];
      byte[] previous = new byte[0];
      int pos = 0;
      for (int i = 1; i <= n; i++) {
        mac.reset();
        mac.update(previous);
        mac.update(info);
        mac.update((byte) i);
        previous = mac.doFinal();
        int take = Math.min(hashLen, length - pos);
        System.arraycopy(previous, 0, okm, pos, take);
        pos += take;
      }
      return okm;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HKDF expand failed", e);
    }
  }

  private static void requirePurpose(String purpose) {
    Objects.requireNonNull(purpose, "purpose must not be null");
    if (purpose.isBlank()) {
      throw new IllegalArgumentException("purpose must not be blank");
    }
  }
}
