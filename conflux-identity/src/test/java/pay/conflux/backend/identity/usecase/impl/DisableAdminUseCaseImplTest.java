package pay.conflux.backend.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.events.UserBlockedEvent;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DisableAdminUseCaseImplTest {

  @Mock private UserRepository userRepository;
  @Mock private AdminProfileRepository adminProfileRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private DisableAdminUseCaseImpl useCase;

  @Test
  void execute_happyPathBlocksUserAndPublishesEvent() {
    UUID targetId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();
    User target = newUser(targetId, UserStatus.ACTIVE);
    AdminProfile profile = newProfile(targetId, AdminTier.VIEWER);

    when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
    when(adminProfileRepository.findByUserId(targetId)).thenReturn(Optional.of(profile));

    useCase.execute(targetId, callerId);

    assertThat(target.getStatus()).isEqualTo(UserStatus.BLOCKED);
    verify(userRepository).save(target);
    verify(eventPublisher).publishEvent(any(UserBlockedEvent.class));
  }

  @Test
  void execute_selfDisableRejected() {
    UUID id = UUID.randomUUID();

    assertThatThrownBy(() -> useCase.execute(id, id))
        .isInstanceOf(InvalidOperationStateException.class)
        .hasMessageContaining("cannot disable themselves");

    verify(userRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void execute_disablingLastSuperRejected() {
    UUID targetId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();
    User target = newUser(targetId, UserStatus.ACTIVE);
    AdminProfile profile = newProfile(targetId, AdminTier.SUPER);

    when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
    when(adminProfileRepository.findByUserId(targetId)).thenReturn(Optional.of(profile));
    when(adminProfileRepository.countActiveSuperAdmins()).thenReturn(1L);

    assertThatThrownBy(() -> useCase.execute(targetId, callerId))
        .isInstanceOf(InvalidOperationStateException.class)
        .hasMessageContaining("last SUPER");

    verify(userRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void execute_disablingSuperWhenOthersExistAllowed() {
    UUID targetId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();
    User target = newUser(targetId, UserStatus.ACTIVE);
    AdminProfile profile = newProfile(targetId, AdminTier.SUPER);

    when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
    when(adminProfileRepository.findByUserId(targetId)).thenReturn(Optional.of(profile));
    when(adminProfileRepository.countActiveSuperAdmins()).thenReturn(2L);

    useCase.execute(targetId, callerId);

    assertThat(target.getStatus()).isEqualTo(UserStatus.BLOCKED);
    verify(eventPublisher).publishEvent(any(UserBlockedEvent.class));
  }

  @Test
  void execute_idempotentOnAlreadyBlockedNoRepublish() {
    UUID targetId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();
    User target = newUser(targetId, UserStatus.BLOCKED);

    when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

    useCase.execute(targetId, callerId);

    verify(userRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  private static User newUser(UUID id, UserStatus status) {
    User u = new User();
    u.setId(id);
    u.setStatus(status);
    u.setDeleted(false);
    return u;
  }

  private static AdminProfile newProfile(UUID userId, AdminTier tier) {
    AdminProfile p = new AdminProfile();
    p.setId(UUID.randomUUID());
    p.setUserId(userId);
    p.setDepartment("Risk");
    p.setEmployeeId("EMP-" + UUID.randomUUID());
    p.setAdminTier(tier);
    return p;
  }
}
