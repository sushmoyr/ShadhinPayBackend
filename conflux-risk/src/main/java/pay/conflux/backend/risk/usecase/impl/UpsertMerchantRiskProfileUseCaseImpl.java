package pay.conflux.backend.risk.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.risk.dto.MerchantRiskProfileDto;
import pay.conflux.backend.risk.dto.UpsertMerchantRiskProfileRequest;
import pay.conflux.backend.risk.entity.MerchantRiskProfile;
import pay.conflux.backend.risk.mapper.MerchantRiskProfileMapper;
import pay.conflux.backend.risk.repository.MerchantRiskProfileRepository;
import pay.conflux.backend.risk.usecase.internal.UpsertMerchantRiskProfileUseCase;

@UseCase
@RequiredArgsConstructor
public class UpsertMerchantRiskProfileUseCaseImpl implements UpsertMerchantRiskProfileUseCase {

  private final MerchantRiskProfileRepository merchantRiskProfileRepository;
  private final MerchantRiskProfileMapper merchantRiskProfileMapper;

  @Override
  @Transactional
  public MerchantRiskProfileDto execute(UUID merchantId, UpsertMerchantRiskProfileRequest request) {
    MerchantRiskProfile profile =
        merchantRiskProfileRepository
            .findByMerchantId(merchantId)
            .orElseGet(
                () -> {
                  MerchantRiskProfile newProfile = new MerchantRiskProfile();
                  newProfile.setMerchantId(merchantId);
                  return newProfile;
                });

    profile.setTrustLevel(request.trustLevel());
    profile.setCustomLimits(request.customLimits());

    MerchantRiskProfile saved = merchantRiskProfileRepository.save(profile);
    return merchantRiskProfileMapper.toDto(saved);
  }
}
