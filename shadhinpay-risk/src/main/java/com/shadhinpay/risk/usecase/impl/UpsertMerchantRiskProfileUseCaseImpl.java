package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.risk.dto.MerchantRiskProfileDto;
import com.shadhinpay.risk.dto.UpsertMerchantRiskProfileRequest;
import com.shadhinpay.risk.entity.MerchantRiskProfile;
import com.shadhinpay.risk.mapper.MerchantRiskProfileMapper;
import com.shadhinpay.risk.repository.MerchantRiskProfileRepository;
import com.shadhinpay.risk.usecase.internal.UpsertMerchantRiskProfileUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

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
