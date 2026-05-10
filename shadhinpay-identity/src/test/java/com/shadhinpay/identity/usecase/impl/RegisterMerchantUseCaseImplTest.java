package com.shadhinpay.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shadhinpay.common.error.DuplicateResourceException;
import com.shadhinpay.identity.dto.MerchantOnboardingDto;
import com.shadhinpay.identity.dto.RegisterMerchantRequest;
import com.shadhinpay.identity.entity.MerchantProfile;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.entity.enums.IdentifierType;
import com.shadhinpay.identity.entity.enums.OnboardingStatus;
import com.shadhinpay.identity.entity.enums.UserStatus;
import com.shadhinpay.identity.mapper.MerchantProfileMapper;
import com.shadhinpay.identity.repository.MerchantProfileRepository;
import com.shadhinpay.identity.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegisterMerchantUseCaseImplTest {

  @Mock private UserRepository userRepository;
  @Mock private MerchantProfileRepository merchantProfileRepository;
  @Mock private MerchantProfileMapper merchantProfileMapper;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private RegisterMerchantUseCaseImpl useCase;

  @Test
  void execute_happyPath() {
    RegisterMerchantRequest request =
        new RegisterMerchantRequest("01712345678", "password123", "John Doe");
    when(userRepository.existsByIdentifierAndIdentifierTypeAndDeletedFalse(
            anyString(), any(IdentifierType.class)))
        .thenReturn(false);
    when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
    when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);
    when(merchantProfileRepository.save(any(MerchantProfile.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    MerchantOnboardingDto expectedDto =
        new MerchantOnboardingDto(
            UUID.randomUUID(),
            "01712345678",
            IdentifierType.PHONE,
            UserStatus.ACTIVE,
            "John Doe",
            OnboardingStatus.REGISTERED);
    when(merchantProfileMapper.toDto(any(User.class), any(MerchantProfile.class)))
        .thenReturn(expectedDto);

    MerchantOnboardingDto result = useCase.execute(request);

    assertThat(result).isEqualTo(expectedDto);
    verify(userRepository).save(any(User.class));
    verify(merchantProfileRepository).save(any(MerchantProfile.class));
  }

  @Test
  void execute_throwsExceptionWhenUserExists() {
    RegisterMerchantRequest request =
        new RegisterMerchantRequest("01712345678", "password123", "John Doe");
    when(userRepository.existsByIdentifierAndIdentifierTypeAndDeletedFalse(
            anyString(), any(IdentifierType.class)))
        .thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(request))
        .isInstanceOf(DuplicateResourceException.class);
  }
}
