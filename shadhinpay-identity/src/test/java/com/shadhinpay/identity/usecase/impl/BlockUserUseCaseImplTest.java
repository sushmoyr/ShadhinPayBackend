package com.shadhinpay.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shadhinpay.identity.dto.BlockUserRequest;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.entity.enums.UserStatus;
import com.shadhinpay.identity.events.UserBlockedEvent;
import com.shadhinpay.identity.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BlockUserUseCaseImplTest {

  @Mock private UserRepository userRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private BlockUserUseCaseImpl useCase;

  @Test
  void execute_Success() {
    UUID userId = UUID.randomUUID();
    BlockUserRequest request = new BlockUserRequest("Suspicious");
    User user = new User();
    user.setId(userId);
    user.setStatus(UserStatus.ACTIVE);
    user.setDeleted(false);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    useCase.execute(userId, request);

    assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);
    verify(userRepository).save(user);
    verify(eventPublisher).publishEvent(any(UserBlockedEvent.class));
  }

  @Test
  void execute_Idempotent_IfAlreadyBlocked() {
    UUID userId = UUID.randomUUID();
    BlockUserRequest request = new BlockUserRequest("Suspicious");
    User user = new User();
    user.setStatus(UserStatus.BLOCKED);
    user.setDeleted(false);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    useCase.execute(userId, request);

    assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);
    verify(userRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }
}
