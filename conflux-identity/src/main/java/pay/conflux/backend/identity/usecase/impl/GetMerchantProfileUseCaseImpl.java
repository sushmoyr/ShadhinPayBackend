package pay.conflux.backend.identity.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.identity.dto.MerchantOnboardingDto;
import pay.conflux.backend.identity.entity.MerchantProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.mapper.MerchantProfileMapper;
import pay.conflux.backend.identity.repository.MerchantProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.usecase.GetMerchantProfileUseCase;

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
