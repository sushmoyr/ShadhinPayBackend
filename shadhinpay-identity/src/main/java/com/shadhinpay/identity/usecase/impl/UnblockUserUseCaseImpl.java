package com.shadhinpay.identity.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.enums.UserStatus;
import com.shadhinpay.identity.repository.UserRepository;
import com.shadhinpay.identity.usecase.UnblockUserUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class UnblockUserUseCaseImpl implements UnblockUserUseCase {

  private final UserRepository userRepository;

  @Override
  @Transactional
  public void execute(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    if (user.isDeleted()) {
      throw new ResourceNotFoundException("User not found");
    }

    if (user.getStatus() == UserStatus.BLOCKED) {
      user.setStatus(UserStatus.ACTIVE);
      userRepository.save(user);
    }
  }
}
