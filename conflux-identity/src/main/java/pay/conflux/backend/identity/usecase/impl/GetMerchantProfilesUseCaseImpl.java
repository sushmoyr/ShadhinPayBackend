package pay.conflux.backend.identity.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.identity.dto.MerchantSummaryDto;
import pay.conflux.backend.identity.entity.MerchantProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.OnboardingStatus;
import pay.conflux.backend.identity.repository.MerchantProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.spec.MerchantSpec;
import pay.conflux.backend.identity.usecase.GetMerchantProfilesUseCase;

@UseCase
@RequiredArgsConstructor
public class GetMerchantProfilesUseCaseImpl implements GetMerchantProfilesUseCase {

  private final MerchantProfileRepository merchantProfileRepository;
  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public Page<MerchantSummaryDto> execute(
      OnboardingStatus status, String search, Pageable pageable) {
    Specification<MerchantProfile> spec =
        Specification.where(MerchantSpec.hasOnboardingStatus(status))
            .and(MerchantSpec.fullNameContains(search));

    return merchantProfileRepository
        .findAll(spec, pageable)
        .map(
            profile -> {
              User user = userRepository.findById(profile.getUserId()).orElse(null);
              return new MerchantSummaryDto(
                  profile.getUserId(),
                  profile.getId(),
                  user != null ? user.getIdentifier() : null,
                  user != null ? user.getIdentifierType() : null,
                  profile.getFullName(),
                  user != null ? user.getStatus() : null,
                  profile.getOnboardingStatus(),
                  profile.getCreatedAt());
            });
  }
}
