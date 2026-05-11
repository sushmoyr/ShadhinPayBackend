package pay.conflux.backend.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pay.conflux.backend.common.error.DuplicateResourceException;
import pay.conflux.backend.identity.dto.MerchantOnboardingDto;
import pay.conflux.backend.identity.dto.RegisterMerchantRequest;
import pay.conflux.backend.identity.entity.MerchantProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.OnboardingStatus;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.mapper.MerchantProfileMapper;
import pay.conflux.backend.identity.repository.MerchantProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RegisterMerchantUseCaseImplTest {

  @Mock private UserRepository userRepository;
  @Mock private MerchantProfileRepository merchantProfileRepository;
  @Mock private MerchantProfileMapper merchantProfileMapper;

  @Spy private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

  @InjectMocks private RegisterMerchantUseCaseImpl useCase;

  @Test
  void execute_happyPath_persistsBCryptHash() {
    RegisterMerchantRequest request =
        new RegisterMerchantRequest("01712345678", "password123", "John Doe");
    when(userRepository.existsByIdentifierAndIdentifierTypeAndDeletedFalse(
            anyString(), any(IdentifierType.class)))
        .thenReturn(false);
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

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    String hash = userCaptor.getValue().getPasswordHash();
    assertThat(hash).startsWith("$2a$10$");
    assertThat(BCrypt.checkpw("password123", hash)).isTrue();

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
