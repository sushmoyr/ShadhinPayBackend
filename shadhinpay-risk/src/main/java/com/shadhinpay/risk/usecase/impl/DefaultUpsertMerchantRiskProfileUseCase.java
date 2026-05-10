package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.risk.dto.MerchantRiskProfileDto;
import com.shadhinpay.risk.dto.UpsertMerchantRiskProfileRequest;
import com.shadhinpay.risk.entity.MerchantRiskProfile;
import com.shadhinpay.risk.mapper.MerchantRiskProfileMapper;
import com.shadhinpay.risk.repository.MerchantRiskProfileRepository;
import com.shadhinpay.risk.usecase.internal.UpsertMerchantRiskProfileUseCase;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultUpsertMerchantRiskProfileUseCase implements UpsertMerchantRiskProfileUseCase {

  private final MerchantRiskProfileRepository merchantRiskProfileRepository;
  private final MerchantRiskProfileMapper merchantRiskProfileMapper;

  public DefaultUpsertMerchantRiskProfileUseCase(
      MerchantRiskProfileRepository merchantRiskProfileRepository,
      MerchantRiskProfileMapper merchantRiskProfileMapper) {
    this.merchantRiskProfileRepository = merchantRiskProfileRepository;
    this.merchantRiskProfileMapper = merchantRiskProfileMapper;
  }

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
