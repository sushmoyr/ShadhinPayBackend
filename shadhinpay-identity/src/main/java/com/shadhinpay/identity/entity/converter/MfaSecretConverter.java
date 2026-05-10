package com.shadhinpay.identity.entity.converter;

import com.shadhinpay.common.crypto.AesGcmCipher;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class MfaSecretConverter extends EncryptedStringConverter {

  public MfaSecretConverter(AesGcmCipher cipher) {
    super(cipher, "mfa-secret");
  }
}
