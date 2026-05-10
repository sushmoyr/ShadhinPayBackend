package com.shadhinpay.identity.usecase.impl;

import com.shadhinpay.common.error.DuplicateResourceException;
import com.shadhinpay.common.util.IdentifierDetector;
import com.shadhinpay.identity.dto.MerchantOnboardingDto;
import com.shadhinpay.identity.dto.RegisterMerchantRequest;
import com.shadhinpay.identity.entity.MerchantProfile;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.entity.enums.IdentifierType;
import com.shadhinpay.identity.entity.enums.OnboardingStatus;
import com.shadhinpay.identity.entity.enums.UserStatus;
import com.shadhinpay.identity.entity.enums.UserType;
import com.shadhinpay.identity.mapper.MerchantProfileMapper;
import com.shadhinpay.identity.repository.MerchantProfileRepository;
import com.shadhinpay.identity.repository.UserRepository;
import com.shadhinpay.identity.usecase.RegisterMerchantUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterMerchantUseCaseImpl implements RegisterMerchantUseCase {

  private final UserRepository userRepository;
  private final MerchantProfileRepository merchantProfileRepository;
  private final MerchantProfileMapper merchantProfileMapper;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional
  public MerchantOnboardingDto execute(RegisterMerchantRequest request) {
    com.shadhinpay.common.util.IdentifierType commonType =
        IdentifierDetector.detect(request.identifier());
    IdentifierType identifierType = IdentifierType.valueOf(commonType.name());

    if (userRepository.existsByIdentifierAndIdentifierTypeAndDeletedFalse(
        request.identifier(), identifierType)) {
      throw new DuplicateResourceException("User already exists with this identifier");
    }

    User user = new User();
    user.setId(UUID.randomUUID());
    user.setIdentifier(request.identifier());
    user.setIdentifierType(identifierType);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setUserType(UserType.MERCHANT);
    user.setStatus(UserStatus.ACTIVE);

    User savedUser = userRepository.save(user);

    MerchantProfile profile = new MerchantProfile();
    profile.setId(UUID.randomUUID());
    profile.setUserId(savedUser.getId());
    profile.setFullName(request.fullName());
    profile.setOnboardingStatus(OnboardingStatus.REGISTERED);

    MerchantProfile savedProfile = merchantProfileRepository.save(profile);

    return merchantProfileMapper.toDto(savedUser, savedProfile);
  }
}
