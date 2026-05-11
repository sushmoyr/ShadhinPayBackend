package com.shadhinpay.identity.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.identity.dto.MerchantSummaryDto;
import com.shadhinpay.identity.entity.MerchantProfile;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.enums.OnboardingStatus;
import com.shadhinpay.identity.repository.MerchantProfileRepository;
import com.shadhinpay.identity.repository.UserRepository;
import com.shadhinpay.identity.spec.MerchantSpec;
import com.shadhinpay.identity.usecase.GetMerchantProfilesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

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
