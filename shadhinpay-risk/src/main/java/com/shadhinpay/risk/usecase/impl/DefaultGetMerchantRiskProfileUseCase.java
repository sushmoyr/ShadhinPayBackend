package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.risk.dto.MerchantRiskProfileDto;
import com.shadhinpay.risk.mapper.MerchantRiskProfileMapper;
import com.shadhinpay.risk.repository.MerchantRiskProfileRepository;
import com.shadhinpay.risk.usecase.internal.GetMerchantRiskProfileUseCase;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultGetMerchantRiskProfileUseCase implements GetMerchantRiskProfileUseCase {

  private final MerchantRiskProfileRepository merchantRiskProfileRepository;
  private final MerchantRiskProfileMapper merchantRiskProfileMapper;

  public DefaultGetMerchantRiskProfileUseCase(
      MerchantRiskProfileRepository merchantRiskProfileRepository,
      MerchantRiskProfileMapper merchantRiskProfileMapper) {
    this.merchantRiskProfileRepository = merchantRiskProfileRepository;
    this.merchantRiskProfileMapper = merchantRiskProfileMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public MerchantRiskProfileDto execute(UUID merchantId) {
    return merchantRiskProfileRepository
        .findByMerchantId(merchantId)
        .map(merchantRiskProfileMapper::toDto)
        .orElseThrow(() -> new ResourceNotFoundException("MerchantRiskProfile", merchantId));
  }
}
