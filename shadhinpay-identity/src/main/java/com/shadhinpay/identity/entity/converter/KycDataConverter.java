package com.shadhinpay.identity.entity.converter;

import com.shadhinpay.common.crypto.AesGcmCipher;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class KycDataConverter extends EncryptedStringConverter {

  public KycDataConverter(AesGcmCipher cipher) {
    super(cipher, "kyc-data");
  }
}
