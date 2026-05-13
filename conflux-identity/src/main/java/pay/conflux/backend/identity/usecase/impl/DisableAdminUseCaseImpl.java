package pay.conflux.backend.identity.usecase.impl;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.events.UserBlockedEvent;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.usecase.DisableAdminUseCase;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class DisableAdminUseCaseImpl implements DisableAdminUseCase {

  private final UserRepository userRepository;
  private final AdminProfileRepository adminProfileRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public void execute(UUID targetUserId, UUID callerUserId) {
    if (targetUserId.equals(callerUserId)) {
      throw new InvalidOperationStateException("Admin cannot disable themselves");
    }

    User user =
        userRepository
            .findById(targetUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId));

    if (user.isDeleted()) {
      throw new ResourceNotFoundException("User", targetUserId);
    }

    if (user.getStatus() == UserStatus.BLOCKED) {
      // Idempotent: already disabled, do not republish event. Mirrors BlockUserUseCase.
      return;
    }

    AdminProfile profile =
        adminProfileRepository
            .findByUserId(targetUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin", targetUserId));

    if (profile.getAdminTier() == AdminTier.SUPER) {
      long activeSuperCount = adminProfileRepository.countActiveSuperAdmins();
      if (activeSuperCount <= 1) {
        throw new InvalidOperationStateException("Cannot disable the last SUPER admin");
      }
    }

    user.setStatus(UserStatus.BLOCKED);
    userRepository.save(user);

    String traceId = MDC.get("traceId");
    if (traceId == null) {
      traceId = UUID.randomUUID().toString();
    }

    eventPublisher.publishEvent(
        new UserBlockedEvent(
            user.getId(), "Admin disabled by SUPER admin", Instant.now(), traceId));
    log.info("Disabled admin {}", targetUserId);
  }
}
