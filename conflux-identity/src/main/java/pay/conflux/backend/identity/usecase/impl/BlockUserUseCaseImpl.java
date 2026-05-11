package pay.conflux.backend.identity.usecase.impl;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.identity.dto.BlockUserRequest;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.events.UserBlockedEvent;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.usecase.BlockUserUseCase;

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
