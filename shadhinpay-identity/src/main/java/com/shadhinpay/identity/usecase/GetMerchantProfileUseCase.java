package com.shadhinpay.identity.usecase;

import com.shadhinpay.identity.dto.MerchantOnboardingDto;
import java.util.UUID;

public interface GetMerchantProfileUseCase {
  MerchantOnboardingDto execute(UUID userId);
}
