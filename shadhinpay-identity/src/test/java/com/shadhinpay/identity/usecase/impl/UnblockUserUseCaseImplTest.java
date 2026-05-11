package com.shadhinpay.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.enums.UserStatus;
import com.shadhinpay.identity.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnblockUserUseCaseImplTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private UnblockUserUseCaseImpl useCase;

  @Test
  void execute_Success() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setStatus(UserStatus.BLOCKED);
    user.setDeleted(false);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    useCase.execute(userId);

    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    verify(userRepository).save(user);
  }

  @Test
  void execute_Idempotent_IfAlreadyActive() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setStatus(UserStatus.ACTIVE);
    user.setDeleted(false);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    useCase.execute(userId);

    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    verify(userRepository, never()).save(any());
  }
}
