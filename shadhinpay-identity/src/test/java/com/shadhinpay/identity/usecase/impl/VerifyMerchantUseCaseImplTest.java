package com.shadhinpay.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shadhinpay.common.error.InvalidOperationStateException;
import com.shadhinpay.identity.entity.MerchantProfile;
import com.shadhinpay.identity.entity.enums.OnboardingStatus;
import com.shadhinpay.identity.events.MerchantVerifiedEvent;
import com.shadhinpay.identity.repository.MerchantProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class VerifyMerchantUseCaseImplTest {

  @Mock private MerchantProfileRepository merchantProfileRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private VerifyMerchantUseCaseImpl useCase;

  @Test
  void execute_Success() {
    UUID profileId = UUID.randomUUID();
    MerchantProfile profile = new MerchantProfile();
    profile.setId(profileId);
    profile.setUserId(UUID.randomUUID());
    profile.setOnboardingStatus(OnboardingStatus.PENDING_VERIFICATION);

    when(merchantProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));

    useCase.execute(profileId);

    assertThat(profile.getOnboardingStatus()).isEqualTo(OnboardingStatus.VERIFIED);
    verify(merchantProfileRepository).save(profile);
    verify(eventPublisher).publishEvent(any(MerchantVerifiedEvent.class));
  }

  @Test
  void execute_ThrowsIfNotPending() {
    UUID profileId = UUID.randomUUID();
    MerchantProfile profile = new MerchantProfile();
    profile.setOnboardingStatus(OnboardingStatus.REGISTERED);

    when(merchantProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));

    assertThatThrownBy(() -> useCase.execute(profileId))
        .isInstanceOf(InvalidOperationStateException.class)
        .hasMessageContaining("Cannot verify merchant from status");
  }
}
