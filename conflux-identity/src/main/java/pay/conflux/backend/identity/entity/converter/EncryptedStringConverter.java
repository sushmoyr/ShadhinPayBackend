package pay.conflux.backend.identity.entity.converter;

import jakarta.persistence.AttributeConverter;
import pay.conflux.backend.common.crypto.AesGcmCipher;

public abstract class EncryptedStringConverter implements AttributeConverter<String, String> {

  private final AesGcmCipher cipher;
  private final String purpose;

  protected EncryptedStringConverter(AesGcmCipher cipher, String purpose) {
    this.cipher = cipher;
    this.purpose = purpose;
  }

  @Override
  public String convertToDatabaseColumn(String attribute) {
    if (attribute == null) {
      return null;
    }
    return cipher.encrypt(attribute, purpose);
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return cipher.decrypt(dbData, purpose);
  }
}
