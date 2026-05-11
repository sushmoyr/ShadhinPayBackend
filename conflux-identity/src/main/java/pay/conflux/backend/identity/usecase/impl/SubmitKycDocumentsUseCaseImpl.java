package pay.conflux.backend.identity.usecase.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.identity.dto.KycSubmissionRequest;
import pay.conflux.backend.identity.dto.MerchantOnboardingDto;
import pay.conflux.backend.identity.entity.MerchantProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.OnboardingStatus;
import pay.conflux.backend.identity.mapper.MerchantProfileMapper;
import pay.conflux.backend.identity.repository.MerchantProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.usecase.SubmitKycDocumentsUseCase;

@UseCase
@RequiredArgsConstructor
public class SubmitKycDocumentsUseCaseImpl implements SubmitKycDocumentsUseCase {

  private final UserRepository userRepository;
  private final MerchantProfileRepository merchantProfileRepository;
  private final MerchantProfileMapper mapper;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  public MerchantOnboardingDto execute(UUID userId, KycSubmissionRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    if (user.isDeleted()) {
      throw new ResourceNotFoundException("User not found");
    }

    MerchantProfile profile =
        merchantProfileRepository
            .findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Merchant profile not found"));

    if (profile.getOnboardingStatus() != OnboardingStatus.REGISTERED) {
      if (profile.getOnboardingStatus() == OnboardingStatus.PENDING_VERIFICATION) {
        throw new InvalidOperationStateException(
            "KYC documents have already been submitted and are pending verification");
      }
      throw new InvalidOperationStateException(
          "Cannot submit KYC from status: " + profile.getOnboardingStatus());
    }

    try {
      String kycDataJson = objectMapper.writeValueAsString(request);
      profile.setKycData(kycDataJson);
      profile.setOnboardingStatus(OnboardingStatus.PENDING_VERIFICATION);
      merchantProfileRepository.save(profile);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize KYC data", e);
    }

    return mapper.toDto(user, profile);
  }
}
