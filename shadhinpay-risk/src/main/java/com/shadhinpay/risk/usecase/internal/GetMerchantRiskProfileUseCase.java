package com.shadhinpay.risk.usecase.internal;

import com.shadhinpay.risk.dto.MerchantRiskProfileDto;
import java.util.UUID;

public interface GetMerchantRiskProfileUseCase {
  MerchantRiskProfileDto execute(UUID merchantId);
}
