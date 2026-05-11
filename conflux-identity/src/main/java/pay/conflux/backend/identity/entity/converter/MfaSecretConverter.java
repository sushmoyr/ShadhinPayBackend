package pay.conflux.backend.identity.entity.converter;

import jakarta.persistence.Converter;
import pay.conflux.backend.common.crypto.AesGcmCipher;

@Converter(autoApply = false)
public class MfaSecretConverter extends EncryptedStringConverter {

  public MfaSecretConverter(AesGcmCipher cipher) {
    super(cipher, "mfa-secret");
  }
}
