package com.shadhinpay.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.identity.dto.MerchantOnboardingDto;
import com.shadhinpay.identity.entity.MerchantProfile;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.entity.enums.OnboardingStatus;
import com.shadhinpay.identity.mapper.MerchantProfileMapper;
import com.shadhinpay.identity.repository.MerchantProfileRepository;
import com.shadhinpay.identity.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetMerchantProfileUseCaseImplTest {

  @Mock private UserRepository userRepository;
  @Mock private MerchantProfileRepository merchantProfileRepository;
  @Mock private MerchantProfileMapper mapper;

  @InjectMocks private GetMerchantProfileUseCaseImpl useCase;

  @Test
  void execute_Success() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setDeleted(false);
    MerchantProfile profile = new MerchantProfile();

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(merchantProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    MerchantOnboardingDto dto =
        new MerchantOnboardingDto(userId, null, null, null, null, OnboardingStatus.REGISTERED);
    when(mapper.toDto(user, profile)).thenReturn(dto);

    MerchantOnboardingDto result = useCase.execute(userId);

    assertThat(result).isNotNull();
    assertThat(result.userId()).isEqualTo(userId);
  }

  @Test
  void execute_ThrowsWhenUserNotFound() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(userId)).isInstanceOf(ResourceNotFoundException.class);
  }
}
