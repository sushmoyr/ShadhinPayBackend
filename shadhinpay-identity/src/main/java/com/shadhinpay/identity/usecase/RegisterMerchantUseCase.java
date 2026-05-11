package com.shadhinpay.identity.usecase;

import com.shadhinpay.identity.dto.MerchantOnboardingDto;
import com.shadhinpay.identity.dto.RegisterMerchantRequest;

public interface RegisterMerchantUseCase {
  MerchantOnboardingDto execute(RegisterMerchantRequest request);
}
