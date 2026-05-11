package pay.conflux.backend.identity.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.identity.dto.RejectMerchantRequest;
import pay.conflux.backend.identity.entity.MerchantProfile;
import pay.conflux.backend.identity.enums.OnboardingStatus;
import pay.conflux.backend.identity.repository.MerchantProfileRepository;
import pay.conflux.backend.identity.usecase.RejectMerchantUseCase;

@UseCase
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
