package pay.conflux.backend.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.identity.dto.MfaEnableResponse;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class EnableMfaUseCaseImplTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private EnableMfaUseCaseImpl useCase;

  @Test
  void execute_generatesSecretAndQrCode() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setIdentifier("01712345678");

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    MfaEnableResponse response = useCase.execute(userId);

    assertThat(response.secret()).isNotBlank();
    assertThat(response.provisioningUri()).startsWith("otpauth://totp/");
    assertThat(response.provisioningUri()).contains("01712345678");
    assertThat(response.qrCodeBase64()).startsWith("data:image/png;base64,");

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User savedUser = captor.getValue();
    assertThat(savedUser.isMfaEnabled()).isTrue();
    assertThat(savedUser.getMfaSecret()).isEqualTo(response.secret());
  }

  @Test
  void execute_whenAlreadyEnabled_throwsInvalidOperationState() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setMfaEnabled(true);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> useCase.execute(userId))
        .isInstanceOf(InvalidOperationStateException.class)
        .hasMessageContaining("MFA is already enabled");
  }

  @Test
  void execute_whenUserNotFound_throwsResourceNotFound() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(userId)).isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void execute_generatedSecretIsDifferentEachTime() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setIdentifier("test@example.com");

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    MfaEnableResponse response1 = useCase.execute(userId);

    user.setMfaEnabled(false);
    user.setMfaSecret(null);

    MfaEnableResponse response2 = useCase.execute(userId);

    assertThat(response1.secret()).isNotEqualTo(response2.secret());
  }
}
