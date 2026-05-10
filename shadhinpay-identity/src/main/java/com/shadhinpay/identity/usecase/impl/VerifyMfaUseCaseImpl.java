package com.shadhinpay.identity.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.error.UnauthorizedException;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.repository.UserRepository;
import com.shadhinpay.identity.usecase.VerifyMfaUseCase;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class VerifyMfaUseCaseImpl implements VerifyMfaUseCase {

  private final UserRepository userRepository;

  private final TimeProvider timeProvider = new SystemTimeProvider();
  private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
  private final CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

  @Override
  @Transactional(readOnly = true)
  public void execute(UUID userId, String code) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UnauthorizedException("Invalid MFA code"));

    String secret = user.getMfaSecret();
    if (secret == null || secret.isBlank()) {
      throw new UnauthorizedException("Invalid MFA code");
    }

    if (!verifier.isValidCode(secret, code)) {
      throw new UnauthorizedException("Invalid MFA code");
    }
  }
}
