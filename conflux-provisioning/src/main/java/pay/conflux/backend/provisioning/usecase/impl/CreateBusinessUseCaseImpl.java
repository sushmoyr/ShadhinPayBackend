package pay.conflux.backend.provisioning.usecase.impl;

import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.crypto.AesGcmCipher;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.constant.EncryptionPurpose;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.dto.CreateBusinessRequest;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.CreateBusinessUseCase;
import pay.conflux.backend.provisioning.usecase.MerchantStatusPort;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class CreateBusinessUseCaseImpl implements CreateBusinessUseCase {

  private static final int WEBHOOK_SECRET_BYTES = 32;

  private final BusinessRepository businessRepository;
  private final ProvisioningMapper mapper;
  private final AesGcmCipher cipher;
  private final MerchantStatusPort merchantStatusPort;
  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  @Transactional
  public BusinessDto execute(java.util.UUID merchantId, CreateBusinessRequest request) {
    if (!merchantStatusPort.isActive(merchantId)) {
      throw new ResourceNotFoundException("Merchant", merchantId);
    }

    Business business = new Business();
    business.setMerchantId(merchantId);
    business.setName(request.getName());
    business.setDisplayName(
        request.getDisplayName() != null && !request.getDisplayName().isBlank()
            ? request.getDisplayName()
            : request.getName());
    business.setLogoUrl(request.getLogoUrl());
    business.setStatus(BusinessStatus.ACTIVE);
    business.setWebhookSecretEncrypted(
        cipher.encrypt(generateWebhookSecret(), EncryptionPurpose.WEBHOOK_SECRET));

    Business saved = businessRepository.save(business);
    log.info("Created business id={} merchantId={}", saved.getId(), merchantId);
    return mapper.toDto(saved);
  }

  private String generateWebhookSecret() {
    byte[] bytes = new byte[WEBHOOK_SECRET_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }
}
