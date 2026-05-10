package com.shadhinpay.identity.usecase.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shadhinpay.common.error.InvalidOperationStateException;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.identity.dto.KycSubmissionRequest;
import com.shadhinpay.identity.dto.MerchantOnboardingDto;
import com.shadhinpay.identity.entity.MerchantProfile;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.entity.enums.OnboardingStatus;
import com.shadhinpay.identity.mapper.MerchantProfileMapper;
import com.shadhinpay.identity.repository.MerchantProfileRepository;
import com.shadhinpay.identity.repository.UserRepository;
import com.shadhinpay.identity.usecase.SubmitKycDocumentsUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
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
