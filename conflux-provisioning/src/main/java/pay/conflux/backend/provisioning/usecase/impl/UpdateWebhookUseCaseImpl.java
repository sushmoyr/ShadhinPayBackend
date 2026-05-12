package pay.conflux.backend.provisioning.usecase.impl;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.crypto.AesGcmCipher;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.provisioning.constant.EncryptionPurpose;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.dto.UpdateWebhookRequest;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.UpdateWebhookUseCase;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class UpdateWebhookUseCaseImpl implements UpdateWebhookUseCase {

  private static final int WEBHOOK_SECRET_BYTES = 32;

  private final BusinessRepository businessRepository;
  private final ProvisioningMapper mapper;
  private final AesGcmCipher cipher;
  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  @Transactional
  public BusinessDto execute(UUID businessId, UpdateWebhookRequest request, boolean rotateSecret) {
    Business business =
        businessRepository
            .findById(businessId)
            .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));

    String url = request.getWebhookUrl();
    if (url == null || !url.startsWith("https://")) {
      throw new ValidationException("webhookUrl must use https://");
    }

    business.setWebhookUrl(url);
    if (rotateSecret || business.getWebhookSecretEncrypted() == null) {
      business.setWebhookSecretEncrypted(
          cipher.encrypt(generateWebhookSecret(), EncryptionPurpose.WEBHOOK_SECRET));
    }

    Business saved = businessRepository.save(business);
    log.info("Updated webhook for business id={} rotated={}", businessId, rotateSecret);

    // TODO(Wave B 8a): enqueue a PING event to the webhook_outbox table once the table is
    // introduced by payment-core. payment-core's dispatcher will then deliver the signed
    // ping to the merchant's new URL. Deferred to keep this sub-prompt module-local.

    return mapper.toDto(saved);
  }

  private String generateWebhookSecret() {
    byte[] bytes = new byte[WEBHOOK_SECRET_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }
}
