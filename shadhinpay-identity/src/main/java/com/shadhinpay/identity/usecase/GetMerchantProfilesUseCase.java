package com.shadhinpay.identity.usecase;

import com.shadhinpay.identity.dto.MerchantSummaryDto;
import com.shadhinpay.identity.entity.enums.OnboardingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetMerchantProfilesUseCase {
  Page<MerchantSummaryDto> execute(OnboardingStatus status, String search, Pageable pageable);
}
