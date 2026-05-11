package com.shadhinpay.risk.mapper;

import com.shadhinpay.risk.dto.MerchantRiskProfileDto;
import com.shadhinpay.risk.entity.MerchantRiskProfile;
import org.springframework.stereotype.Component;

@Component
public class MerchantRiskProfileMapper {

  public MerchantRiskProfileDto toDto(MerchantRiskProfile entity) {
    if (entity == null) {
      return null;
    }
    return new MerchantRiskProfileDto(
        entity.getMerchantId(), entity.getTrustLevel(), entity.getCustomLimits());
  }
}
