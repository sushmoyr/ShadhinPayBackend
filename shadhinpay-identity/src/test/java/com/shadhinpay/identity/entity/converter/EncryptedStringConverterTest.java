package com.shadhinpay.identity.entity.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shadhinpay.common.crypto.AesGcmCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EncryptedStringConverterTest {

  @Mock private AesGcmCipher cipher;

  private MfaSecretConverter converter;

  @BeforeEach
  void setUp() {
    converter = new MfaSecretConverter(cipher);
  }

  @Test
  void convertToDatabaseColumn_encrypts() {
    String plaintext = "secret-123";
    String ciphertext = "encrypted-abc";
    when(cipher.encrypt(plaintext, "mfa-secret")).thenReturn(ciphertext);

    String result = converter.convertToDatabaseColumn(plaintext);

    assertThat(result).isEqualTo(ciphertext);
    verify(cipher).encrypt(plaintext, "mfa-secret");
  }

  @Test
  void convertToDatabaseColumn_handlesNull() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
  }

  @Test
  void convertToEntityAttribute_decrypts() {
    String ciphertext = "encrypted-abc";
    String plaintext = "secret-123";
    when(cipher.decrypt(ciphertext, "mfa-secret")).thenReturn(plaintext);

    String result = converter.convertToEntityAttribute(ciphertext);

    assertThat(result).isEqualTo(plaintext);
    verify(cipher).decrypt(ciphertext, "mfa-secret");
  }

  @Test
  void convertToEntityAttribute_handlesNull() {
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }
}
