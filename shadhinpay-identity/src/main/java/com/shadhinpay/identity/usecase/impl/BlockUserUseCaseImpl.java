package com.shadhinpay.identity.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.identity.dto.BlockUserRequest;
import com.shadhinpay.identity.entity.User;
import com.shadhinpay.identity.enums.UserStatus;
import com.shadhinpay.identity.events.UserBlockedEvent;
import com.shadhinpay.identity.repository.UserRepository;
import com.shadhinpay.identity.usecase.BlockUserUseCase;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class BlockUserUseCaseImpl implements BlockUserUseCase {

  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public void execute(UUID userId, BlockUserRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    if (user.isDeleted()) {
      throw new ResourceNotFoundException("User not found");
    }

    if (user.getStatus() == UserStatus.BLOCKED) {
      // Idempotent: already blocked, do not republish event
      return;
    }

    user.setStatus(UserStatus.BLOCKED);
    userRepository.save(user);

    String traceId = MDC.get("traceId");
    if (traceId == null) {
      traceId = UUID.randomUUID().toString(); // Fallback if traceId is missing
    }

    String reason =
        (request != null && request.reason() != null) ? request.reason() : "No reason provided";

    eventPublisher.publishEvent(new UserBlockedEvent(user.getId(), reason, Instant.now(), traceId));
  }
}
