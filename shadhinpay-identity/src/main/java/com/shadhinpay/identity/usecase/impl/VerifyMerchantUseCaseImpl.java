package com.shadhinpay.identity.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.error.InvalidOperationStateException;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.identity.entity.MerchantProfile;
import com.shadhinpay.identity.enums.OnboardingStatus;
import com.shadhinpay.identity.events.MerchantVerifiedEvent;
import com.shadhinpay.identity.repository.MerchantProfileRepository;
import com.shadhinpay.identity.usecase.VerifyMerchantUseCase;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class VerifyMerchantUseCaseImpl implements VerifyMerchantUseCase {

  private final MerchantProfileRepository merchantProfileRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public void execute(UUID merchantProfileId) {
    MerchantProfile profile =
        merchantProfileRepository
            .findById(merchantProfileId)
            .orElseThrow(() -> new ResourceNotFoundException("Merchant profile not found"));

    if (profile.getOnboardingStatus() != OnboardingStatus.PENDING_VERIFICATION) {
      throw new InvalidOperationStateException(
          "Cannot verify merchant from status: " + profile.getOnboardingStatus());
    }

    profile.setOnboardingStatus(OnboardingStatus.VERIFIED);
    merchantProfileRepository.save(profile);

    String traceId = MDC.get("traceId");
    if (traceId == null) {
      traceId = UUID.randomUUID().toString(); // Fallback if traceId is missing in test context
    }

    eventPublisher.publishEvent(
        new MerchantVerifiedEvent(profile.getUserId(), profile.getId(), Instant.now(), traceId));
  }
}
