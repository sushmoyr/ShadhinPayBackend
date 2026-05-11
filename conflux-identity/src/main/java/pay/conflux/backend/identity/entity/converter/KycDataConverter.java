package pay.conflux.backend.identity.entity.converter;

import jakarta.persistence.Converter;
import pay.conflux.backend.common.crypto.AesGcmCipher;

@Converter(autoApply = false)
public class KycDataConverter extends EncryptedStringConverter {

  public KycDataConverter(AesGcmCipher cipher) {
    super(cipher, "kyc-data");
  }
}
