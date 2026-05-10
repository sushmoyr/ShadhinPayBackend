package com.shadhinpay.identity.usecase;

import com.shadhinpay.identity.dto.KycSubmissionRequest;
import com.shadhinpay.identity.dto.MerchantOnboardingDto;
import java.util.UUID;

public interface SubmitKycDocumentsUseCase {
  MerchantOnboardingDto execute(UUID userId, KycSubmissionRequest request);
}
