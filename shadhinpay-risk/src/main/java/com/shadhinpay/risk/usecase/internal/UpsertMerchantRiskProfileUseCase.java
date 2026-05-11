package com.shadhinpay.risk.usecase.internal;

import com.shadhinpay.risk.dto.MerchantRiskProfileDto;
import com.shadhinpay.risk.dto.UpsertMerchantRiskProfileRequest;
import java.util.UUID;

public interface UpsertMerchantRiskProfileUseCase {
  MerchantRiskProfileDto execute(UUID merchantId, UpsertMerchantRiskProfileRequest request);
}
