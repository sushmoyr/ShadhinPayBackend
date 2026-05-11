package com.shadhinpay.identity.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.error.InvalidOperationStateException;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.common.error.UnauthorizedException;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.repository.UserRepository;
import com.shadhinpay.identity.usecase.DisableMfaUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

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
