package pay.conflux.backend.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.identity.dto.AdminProfileDto;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.enums.UserType;
import pay.conflux.backend.identity.mapper.AdminProfileMapper;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class GetAdminProfileUseCaseImplTest {

  @Mock private UserRepository userRepository;
  @Mock private AdminProfileRepository adminProfileRepository;
  @Mock private AdminProfileMapper adminProfileMapper;

  @InjectMocks private GetAdminProfileUseCaseImpl useCase;

  @Test
  void execute_returnsDtoFromMapper() {
    UUID userId = UUID.randomUUID();
    User user = newUser(userId);
    AdminProfile profile = newProfile(userId);
    AdminProfileDto dto =
        new AdminProfileDto(
            userId,
            profile.getId(),
            user.getIdentifier(),
            IdentifierType.EMAIL,
            UserStatus.ACTIVE,
            profile.getDepartment(),
            profile.getEmployeeId(),
            AdminTier.MANAGER);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(adminProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(adminProfileMapper.toDto(user, profile)).thenReturn(dto);

    assertThat(useCase.execute(userId)).isSameAs(dto);
  }

  @Test
  void execute_throwsWhenUserMissing() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Admin");
  }

  @Test
  void execute_throwsWhenUserSoftDeleted() {
    UUID userId = UUID.randomUUID();
    User user = newUser(userId);
    user.setDeleted(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> useCase.execute(userId)).isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void execute_throwsWhenProfileMissing() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(newUser(userId)));
    when(adminProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("AdminProfile");
  }

  private static User newUser(UUID id) {
    User u = new User();
    u.setId(id);
    u.setIdentifier("me@example.com");
    u.setIdentifierType(IdentifierType.EMAIL);
    u.setStatus(UserStatus.ACTIVE);
    u.setUserType(UserType.ADMIN);
    u.setPasswordHash("hash");
    return u;
  }

  private static AdminProfile newProfile(UUID userId) {
    AdminProfile p = new AdminProfile();
    p.setId(UUID.randomUUID());
    p.setUserId(userId);
    p.setDepartment("Ops");
    p.setEmployeeId("EMP-100");
    p.setAdminTier(AdminTier.MANAGER);
    return p;
  }
}
