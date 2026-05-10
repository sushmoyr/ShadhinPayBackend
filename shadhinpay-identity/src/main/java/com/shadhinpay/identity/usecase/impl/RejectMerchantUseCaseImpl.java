package com.shadhinpay.identity.usecase.impl;

import com.shadhinpay.common.error.InvalidOperationStateException;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.identity.dto.RejectMerchantRequest;
import com.shadhinpay.identity.entity.MerchantProfile;
import com.shadhinpay.identity.entity.enums.OnboardingStatus;
import com.shadhinpay.identity.repository.MerchantProfileRepository;
import com.shadhinpay.identity.usecase.RejectMerchantUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RejectMerchantUseCaseImpl implements RejectMerchantUseCase {

  private final MerchantProfileRepository merchantProfileRepository;

  @Override
  @Transactional
  public void execute(UUID merchantProfileId, RejectMerchantRequest request) {
    MerchantProfile profile =
        merchantProfileRepository
            .findById(merchantProfileId)
            .orElseThrow(() -> new ResourceNotFoundException("Merchant profile not found"));

    if (profile.getOnboardingStatus() != OnboardingStatus.PENDING_VERIFICATION) {
      throw new InvalidOperationStateException(
          "Cannot reject merchant from status: " + profile.getOnboardingStatus());
    }

    // Reason validation is handled by Spring Validation at the controller layer (@Valid)
    // We would store the reason in an audit log or an entity field, but for now we just change
    // state.
    profile.setOnboardingStatus(OnboardingStatus.REJECTED);
    merchantProfileRepository.save(profile);
  }
}
