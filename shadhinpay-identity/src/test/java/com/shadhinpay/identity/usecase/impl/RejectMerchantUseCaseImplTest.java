package com.shadhinpay.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shadhinpay.common.error.InvalidOperationStateException;
import com.shadhinpay.identity.dto.RejectMerchantRequest;
import com.shadhinpay.identity.entity.MerchantProfile;
import com.shadhinpay.identity.entity.enums.OnboardingStatus;
import com.shadhinpay.identity.repository.MerchantProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RejectMerchantUseCaseImplTest {

  @Mock private MerchantProfileRepository merchantProfileRepository;

  @InjectMocks private RejectMerchantUseCaseImpl useCase;

  @Test
  void execute_Success() {
    UUID profileId = UUID.randomUUID();
    RejectMerchantRequest request = new RejectMerchantRequest("Incomplete documents");
    MerchantProfile profile = new MerchantProfile();
    profile.setOnboardingStatus(OnboardingStatus.PENDING_VERIFICATION);

    when(merchantProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));

    useCase.execute(profileId, request);

    assertThat(profile.getOnboardingStatus()).isEqualTo(OnboardingStatus.REJECTED);
    verify(merchantProfileRepository).save(profile);
  }

  @Test
  void execute_ThrowsIfNotPending() {
    UUID profileId = UUID.randomUUID();
    RejectMerchantRequest request = new RejectMerchantRequest("reason");
    MerchantProfile profile = new MerchantProfile();
    profile.setOnboardingStatus(OnboardingStatus.REGISTERED);

    when(merchantProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));

    assertThatThrownBy(() -> useCase.execute(profileId, request))
        .isInstanceOf(InvalidOperationStateException.class);
  }
}
