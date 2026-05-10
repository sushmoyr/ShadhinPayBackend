package com.shadhinpay.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shadhinpay.common.error.InvalidOperationStateException;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.common.error.UnauthorizedException;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DisableMfaUseCaseImplTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private DisableMfaUseCaseImpl useCase;

  @Test
  void execute_happyPath_clearsSecretAndDisablesMfa() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setMfaEnabled(true);
    user.setMfaSecret("some-encrypted-secret");
    user.setPasswordHash("hashed-password");

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);

    assertThatCode(() -> useCase.execute(userId, "correct-password")).doesNotThrowAnyException();

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User savedUser = captor.getValue();
    assertThat(savedUser.isMfaEnabled()).isFalse();
    assertThat(savedUser.getMfaSecret()).isNull();
  }

  @Test
  void execute_whenNotEnabled_throwsInvalidOperationState() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setMfaEnabled(false);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> useCase.execute(userId, "password"))
        .isInstanceOf(InvalidOperationStateException.class)
        .hasMessageContaining("MFA is not enabled");
  }

  @Test
  void execute_wrongPassword_throwsUnauthorized() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setMfaEnabled(true);
    user.setPasswordHash("hashed-password");

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(userId, "wrong-password"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid password");
  }

  @Test
  void execute_userNotFound_throwsResourceNotFound() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(userId, "password"))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
