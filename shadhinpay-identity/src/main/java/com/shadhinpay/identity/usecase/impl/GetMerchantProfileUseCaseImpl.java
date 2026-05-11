package com.shadhinpay.identity.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.identity.dto.MerchantOnboardingDto;
import com.shadhinpay.identity.entity.MerchantProfile;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.mapper.MerchantProfileMapper;
import com.shadhinpay.identity.repository.MerchantProfileRepository;
import com.shadhinpay.identity.repository.UserRepository;
import com.shadhinpay.identity.usecase.GetMerchantProfileUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class GetMerchantProfileUseCaseImpl implements GetMerchantProfileUseCase {

  private final UserRepository userRepository;
  private final MerchantProfileRepository merchantProfileRepository;
  private final MerchantProfileMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public MerchantOnboardingDto execute(UUID userId) {
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

    return mapper.toDto(user, profile);
  }
}
