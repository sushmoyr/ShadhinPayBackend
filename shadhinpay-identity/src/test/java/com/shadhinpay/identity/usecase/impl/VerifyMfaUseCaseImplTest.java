package com.shadhinpay.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.shadhinpay.common.error.UnauthorizedException;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.repository.UserRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerifyMfaUseCaseImplTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private VerifyMfaUseCaseImpl useCase;

  private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
  private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
  private final TimeProvider timeProvider = new SystemTimeProvider();

  private String generateCode(String secret, long counter) {
    try {
      return codeGenerator.generate(secret, counter);
    } catch (CodeGenerationException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void execute_validCode_succeeds() {
    String secret = secretGenerator.generate();
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setMfaSecret(secret);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    long counter = timeProvider.getTime() / 30;
    String code = generateCode(secret, counter);

    assertThatCode(() -> useCase.execute(userId, code)).doesNotThrowAnyException();
  }

  @Test
  void execute_invalidCode_throwsUnauthorized() {
    String secret = secretGenerator.generate();
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setMfaSecret(secret);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> useCase.execute(userId, "000000"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid MFA code");
  }

  @Test
  void execute_userNotFound_throwsUnauthorized() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(userId, "123456"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid MFA code");
  }

  @Test
  void execute_nullMfaSecret_throwsUnauthorized() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setMfaSecret(null);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> useCase.execute(userId, "123456"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid MFA code");
  }

  @Test
  void execute_wrongSecretDifferentUser_throwsUnauthorized() {
    String secret1 = secretGenerator.generate();
    String secret2 = secretGenerator.generate();

    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setMfaSecret(secret1);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    long counter = timeProvider.getTime() / 30;
    String codeFromDifferentSecret = generateCode(secret2, counter);

    assertThatThrownBy(() -> useCase.execute(userId, codeFromDifferentSecret))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid MFA code");
  }

  @Test
  void execute_acceptsCodeWithinSkewWindow() {
    String secret = secretGenerator.generate();
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setMfaSecret(secret);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    // Code from 30 seconds ago (previous time step) should be accepted with discrepancy=1
    long counter30sAgo = (timeProvider.getTime() - 30) / 30;
    String code30sAgo = generateCode(secret, counter30sAgo);

    assertThatCode(() -> useCase.execute(userId, code30sAgo)).doesNotThrowAnyException();
  }

  @Test
  void execute_rejectsCodeOutsideSkewWindow() {
    String secret = secretGenerator.generate();
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setMfaSecret(secret);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    // Code from 90 seconds ago should be rejected with discrepancy=1
    long counter90sAgo = (timeProvider.getTime() - 90) / 30;
    String code90sAgo = generateCode(secret, counter90sAgo);

    assertThatThrownBy(() -> useCase.execute(userId, code90sAgo))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid MFA code");
  }
}
