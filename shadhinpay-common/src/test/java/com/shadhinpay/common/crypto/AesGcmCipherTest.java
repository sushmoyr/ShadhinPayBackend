package com.shadhinpay.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;

class AesGcmCipherTest {

  private static byte[] randomKey() {
    byte[] k = new byte[32];
    new SecureRandom().nextBytes(k);
    return k;
  }

  @Test
  void rejectsKeyOfWrongLength() {
    assertThatThrownBy(() -> new AesGcmCipher(new byte[16]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void encryptThenDecrypt_roundTripsKnownValue() {
    AesGcmCipher cipher = new AesGcmCipher(randomKey());
    String ct = cipher.encrypt("hello world", "vendor-credentials");
    assertThat(cipher.decrypt(ct, "vendor-credentials")).isEqualTo("hello world");
  }

  @Test
  void sameInput_producesDifferentCiphertextEachTime() {
    AesGcmCipher cipher = new AesGcmCipher(randomKey());
    String a = cipher.encrypt("payload", "mfa-secret");
    String b = cipher.encrypt("payload", "mfa-secret");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void differentPurpose_failsToDecrypt() {
    AesGcmCipher cipher = new AesGcmCipher(randomKey());
    String ct = cipher.encrypt("topsecret", "vendor-credentials");
    assertThatThrownBy(() -> cipher.decrypt(ct, "mfa-secret"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void tamperedCiphertext_fails() {
    AesGcmCipher cipher = new AesGcmCipher(randomKey());
    String ct = cipher.encrypt("payload", "mfa-secret");
    byte[] raw = Base64.getDecoder().decode(ct);
    raw[AesGcmCipher.IV_BYTES + 4] ^= 0xFF;
    String tampered = Base64.getEncoder().encodeToString(raw);
    assertThatThrownBy(() -> cipher.decrypt(tampered, "mfa-secret"))
        .isInstanceOfAny(IllegalStateException.class, IllegalArgumentException.class);
  }

  @Test
  void rejectsNonBase64Ciphertext() {
    AesGcmCipher cipher = new AesGcmCipher(randomKey());
    assertThatThrownBy(() -> cipher.decrypt("@@@not base64@@@", "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsTooShortCiphertext() {
    AesGcmCipher cipher = new AesGcmCipher(randomKey());
    assertThatThrownBy(() -> cipher.decrypt("AAAA", "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankPurpose() {
    AesGcmCipher cipher = new AesGcmCipher(randomKey());
    assertThatThrownBy(() -> cipher.encrypt("x", " ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Property(tries = 100)
  void roundTrip_preservesPlaintext(
      @ForAll @StringLength(min = 0, max = 256) String plaintext,
      @ForAll("purposes") String purpose) {
    AesGcmCipher cipher = new AesGcmCipher(randomKey());
    String ct = cipher.encrypt(plaintext, purpose);
    assertThat(cipher.decrypt(ct, purpose)).isEqualTo(plaintext);
  }

  @Provide
  Arbitrary<String> purposes() {
    return Arbitraries.of("vendor-credentials", "mfa-secret", "api-key", "webhook-token");
  }
}
