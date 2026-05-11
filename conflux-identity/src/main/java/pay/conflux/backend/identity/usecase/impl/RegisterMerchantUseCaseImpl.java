package pay.conflux.backend.identity.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.DuplicateResourceException;
import pay.conflux.backend.common.util.IdentifierDetector;
import pay.conflux.backend.identity.dto.MerchantOnboardingDto;
import pay.conflux.backend.identity.dto.RegisterMerchantRequest;
import pay.conflux.backend.identity.entity.MerchantProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.OnboardingStatus;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.enums.UserType;
import pay.conflux.backend.identity.mapper.MerchantProfileMapper;
import pay.conflux.backend.identity.repository.MerchantProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.usecase.RegisterMerchantUseCase;

@UseCase
@RequiredArgsConstructor
public class RegisterMerchantUseCaseImpl implements RegisterMerchantUseCase {

  private final UserRepository userRepository;
  private final MerchantProfileRepository merchantProfileRepository;
  private final MerchantProfileMapper merchantProfileMapper;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional
  public MerchantOnboardingDto execute(RegisterMerchantRequest request) {
    pay.conflux.backend.common.util.IdentifierType commonType =
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
