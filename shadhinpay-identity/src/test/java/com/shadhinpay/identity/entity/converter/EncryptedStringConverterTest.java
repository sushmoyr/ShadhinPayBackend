package com.shadhinpay.identity.entity.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.shadhinpay.common.crypto.AesGcmCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link MfaSecretConverter} (and by inheritance, the abstract {@code
 * EncryptedStringConverter}) against a real {@link AesGcmCipher} instance. Mocking the cipher would
 * hide regressions in IV randomness and tamper detection, so a deterministic 32-byte test key is
 * used to assert real round-trip behavior.
 */
class EncryptedStringConverterTest {

  private static final byte[] TEST_KEY = new byte[32];

  static {
    for (int i = 0; i < TEST_KEY.length; i++) {
      TEST_KEY[i] = (byte) i;
    }
  }

  private AesGcmCipher cipher;
  private MfaSecretConverter converter;

  @BeforeEach
  void setUp() {
    cipher = new AesGcmCipher(TEST_KEY);
    converter = new MfaSecretConverter(cipher);
  }

  @Test
  void convertToDatabaseColumn_thenConvertToEntityAttribute_roundTrips() {
    String plaintext = "JBSWY3DPEHPK3PXP";

    String ciphertext = converter.convertToDatabaseColumn(plaintext);
    String decrypted = converter.convertToEntityAttribute(ciphertext);

    assertThat(decrypted).isEqualTo(plaintext);
    assertThat(ciphertext).isNotEqualTo(plaintext);
  }

  @Test
  void convertToDatabaseColumn_isNonDeterministic_dueToRandomIv() {
    String plaintext = "JBSWY3DPEHPK3PXP";

    String ciphertext1 = converter.convertToDatabaseColumn(plaintext);
    String ciphertext2 = converter.convertToDatabaseColumn(plaintext);

    assertThat(ciphertext1).isNotEqualTo(ciphertext2);
    assertThat(converter.convertToEntityAttribute(ciphertext1)).isEqualTo(plaintext);
    assertThat(converter.convertToEntityAttribute(ciphertext2)).isEqualTo(plaintext);
  }

  @Test
  void convertToDatabaseColumn_handlesNull() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
  }

  @Test
  void convertToEntityAttribute_handlesNull() {
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }
}
