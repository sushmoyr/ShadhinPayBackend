package pay.conflux.backend.identity.usecase.impl;

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
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.usecase.VerifyMfaUseCase;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class VerifyMfaUseCaseImpl implements VerifyMfaUseCase {

  private final UserRepository userRepository;

  private final TimeProvider timeProvider = new SystemTimeProvider();
  private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
  private final DefaultCodeVerifier defaultVerifier =
      new DefaultCodeVerifier(codeGenerator, timeProvider);
  private final CodeVerifier verifier = configureVerifier(defaultVerifier);

  private static CodeVerifier configureVerifier(DefaultCodeVerifier verifier) {
    verifier.setAllowedTimePeriodDiscrepancy(1);
    return verifier;
  }

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
