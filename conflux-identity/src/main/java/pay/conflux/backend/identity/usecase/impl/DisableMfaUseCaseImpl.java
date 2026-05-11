package pay.conflux.backend.identity.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.usecase.DisableMfaUseCase;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class DisableMfaUseCaseImpl implements DisableMfaUseCase {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional
  public void execute(UUID userId, String password) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));

    if (!user.isMfaEnabled()) {
      throw new InvalidOperationStateException("MFA is not enabled for this user");
    }

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new UnauthorizedException("Invalid password");
    }

    user.setMfaSecret(null);
    user.setMfaEnabled(false);
    userRepository.save(user);
  }
}
