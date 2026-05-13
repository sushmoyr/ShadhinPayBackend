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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.identity.dto.AdminProfileDto;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.mapper.AdminProfileMapper;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UpdateAdminTierUseCaseImplTest {

  @Mock private UserRepository userRepository;
  @Mock private AdminProfileRepository adminProfileRepository;
  @Mock private AdminProfileMapper adminProfileMapper;

  @InjectMocks private UpdateAdminTierUseCaseImpl useCase;

  @ParameterizedTest
  @CsvSource({"VIEWER,MANAGER", "MANAGER,SUPER", "VIEWER,SUPER"})
  void execute_promotionPathsPersistNewTier(AdminTier from, AdminTier to) {
    UUID userId = UUID.randomUUID();
    AdminProfile profile = newProfile(userId, from);
    User user = newUser(userId);

    when(adminProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(adminProfileMapper.toDto(user, profile)).thenReturn(stubDto(to));

    AdminProfileDto result = useCase.execute(userId, to);

    assertThat(profile.getAdminTier()).isEqualTo(to);
    verify(adminProfileRepository).save(profile);
    assertThat(result.adminTier()).isEqualTo(to);
  }

  @Test
  void execute_sameTierIsIdempotentNoSave() {
    UUID userId = UUID.randomUUID();
    AdminProfile profile = newProfile(userId, AdminTier.MANAGER);
    User user = newUser(userId);

    when(adminProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(adminProfileMapper.toDto(user, profile)).thenReturn(stubDto(AdminTier.MANAGER));

    useCase.execute(userId, AdminTier.MANAGER);

    verify(adminProfileRepository, never()).save(any());
  }

  @ParameterizedTest
  @CsvSource({"VIEWER", "MANAGER"})
  void execute_demotingLastSuperRejected(AdminTier newTier) {
    UUID userId = UUID.randomUUID();
    AdminProfile profile = newProfile(userId, AdminTier.SUPER);
    User user = newUser(userId);

    when(adminProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(adminProfileRepository.countActiveSuperAdmins()).thenReturn(1L);

    assertThatThrownBy(() -> useCase.execute(userId, newTier))
        .isInstanceOf(InvalidOperationStateException.class)
        .hasMessageContaining("last SUPER");

    verify(adminProfileRepository, never()).save(any());
  }

  @Test
  void execute_demotingSuperWhenMultipleExistAllowed() {
    UUID userId = UUID.randomUUID();
    AdminProfile profile = newProfile(userId, AdminTier.SUPER);
    User user = newUser(userId);

    when(adminProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(adminProfileRepository.countActiveSuperAdmins()).thenReturn(2L);
    when(adminProfileMapper.toDto(user, profile)).thenReturn(stubDto(AdminTier.MANAGER));

    useCase.execute(userId, AdminTier.MANAGER);

    assertThat(profile.getAdminTier()).isEqualTo(AdminTier.MANAGER);
    verify(adminProfileRepository).save(profile);
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

  private static User newUser(UUID id) {
    User u = new User();
    u.setId(id);
    u.setIdentifier("admin@example.com");
    u.setIdentifierType(IdentifierType.EMAIL);
    u.setStatus(UserStatus.ACTIVE);
    u.setPasswordHash("hash");
    return u;
  }

  private static AdminProfileDto stubDto(AdminTier tier) {
    return new AdminProfileDto(
        null,
        null,
        "admin@example.com",
        IdentifierType.EMAIL,
        UserStatus.ACTIVE,
        "Risk",
        "EMP-X",
        tier);
  }
}
