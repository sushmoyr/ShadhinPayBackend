package pay.conflux.backend.provisioning.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pay.conflux.backend.common.crypto.AesGcmCipher;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.provisioning.constant.EncryptionPurpose;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.BusinessWebhookConfigDescriptor;
import pay.conflux.backend.provisioning.usecase.GetBusinessWebhookConfigUseCase;

/**
 * Hot-path resolver for the merchant webhook target. Decrypts the secret inline so callers (the
 * payment-core dispatcher) never see the ciphertext and never need to know the encryption purpose
 * literal. Never logs the secret.
 */
@Component
@RequiredArgsConstructor
public class GetBusinessWebhookConfigUseCaseImpl implements GetBusinessWebhookConfigUseCase {

  private final BusinessRepository businessRepository;
  private final AesGcmCipher cipher;

  @Override
  public BusinessWebhookConfigDescriptor execute(UUID businessId) {
    Business business =
        businessRepository
            .findById(businessId)
            .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    String url = business.getWebhookUrl();
    if (url == null || url.isBlank()) {
      return BusinessWebhookConfigDescriptor.empty();
    }
    String secretEncrypted = business.getWebhookSecretEncrypted();
    if (secretEncrypted == null || secretEncrypted.isBlank()) {
      return BusinessWebhookConfigDescriptor.empty();
    }
    String decrypted = cipher.decrypt(secretEncrypted, EncryptionPurpose.WEBHOOK_SECRET);
    return new BusinessWebhookConfigDescriptor(url, decrypted);
  }
}
