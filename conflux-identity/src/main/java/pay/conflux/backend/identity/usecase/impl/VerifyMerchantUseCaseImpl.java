package pay.conflux.backend.identity.usecase.impl;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.identity.entity.MerchantProfile;
import pay.conflux.backend.identity.enums.OnboardingStatus;
import pay.conflux.backend.identity.events.MerchantVerifiedEvent;
import pay.conflux.backend.identity.repository.MerchantProfileRepository;
import pay.conflux.backend.identity.usecase.VerifyMerchantUseCase;

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
