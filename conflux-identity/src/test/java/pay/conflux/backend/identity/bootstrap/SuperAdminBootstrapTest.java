package pay.conflux.backend.identity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.enums.UserType;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class SuperAdminBootstrapTest {

  @Mock private UserRepository userRepository;
  @Mock private AdminProfileRepository adminProfileRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private SuperAdminBootstrap bootstrap;

  @BeforeEach
  void resetConfig() {
    ReflectionTestUtils.setField(bootstrap, "configuredIdentifier", "");
    ReflectionTestUtils.setField(bootstrap, "configuredPassword", "");
  }

  @Test
  void run_blankEnvVarsNoExistingSuper_failsFast() {
    when(adminProfileRepository.countActiveSuperAdmins()).thenReturn(0L);

    assertThatThrownBy(() -> bootstrap.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SUPER admin");
  }

  @Test
  void run_blankEnvVarsExistingSuper_isNoop() {
    when(adminProfileRepository.countActiveSuperAdmins()).thenReturn(1L);

    bootstrap.run(null);

    verify(userRepository, never()).save(any());
    verify(adminProfileRepository, never()).save(any());
  }

  @Test
  void run_onlyIdentifierBlank_throwsConfigError() {
    ReflectionTestUtils.setField(bootstrap, "configuredIdentifier", "");
    ReflectionTestUtils.setField(bootstrap, "configuredPassword", "some-password");

    assertThatThrownBy(() -> bootstrap.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("both be set or both blank");
  }

  @Test
  void run_onlyPasswordBlank_throwsConfigError() {
    ReflectionTestUtils.setField(bootstrap, "configuredIdentifier", "super@example.com");
    ReflectionTestUtils.setField(bootstrap, "configuredPassword", "");

    assertThatThrownBy(() -> bootstrap.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("both be set or both blank");
  }

  @Test
  void run_envVarsSetUserDoesNotExist_createsSuperAdmin() {
    ReflectionTestUtils.setField(bootstrap, "configuredIdentifier", "fresh@example.com");
    ReflectionTestUtils.setField(bootstrap, "configuredPassword", "fresh-password");

    when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(
            "fresh@example.com", IdentifierType.EMAIL))
        .thenReturn(Optional.empty());
    when(passwordEncoder.encode("fresh-password")).thenReturn("$2a$10$fresh");

    bootstrap.run(null);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getUserType()).isEqualTo(UserType.ADMIN);
    assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("$2a$10$fresh");

    ArgumentCaptor<AdminProfile> profileCaptor = ArgumentCaptor.forClass(AdminProfile.class);
    verify(adminProfileRepository).save(profileCaptor.capture());
    assertThat(profileCaptor.getValue().getAdminTier()).isEqualTo(AdminTier.SUPER);
  }

  @Test
  void run_existingUserMatchingPassword_isNoop() {
    ReflectionTestUtils.setField(bootstrap, "configuredIdentifier", "ok@example.com");
    ReflectionTestUtils.setField(bootstrap, "configuredPassword", "matching");
    UUID userId = UUID.randomUUID();
    User existing = newAdmin(userId, "ok@example.com", UserStatus.ACTIVE, "$2a$10$existing");
    AdminProfile profile = newProfile(userId, AdminTier.SUPER);

    when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(
            "ok@example.com", IdentifierType.EMAIL))
        .thenReturn(Optional.of(existing));
    when(adminProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(passwordEncoder.matches("matching", "$2a$10$existing")).thenReturn(true);

    bootstrap.run(null);

    verify(userRepository, never()).save(any());
    verify(adminProfileRepository, never()).save(any());
  }

  @Test
  void run_existingUserDifferentPassword_rotatesHash() {
    ReflectionTestUtils.setField(bootstrap, "configuredIdentifier", "rot@example.com");
    ReflectionTestUtils.setField(bootstrap, "configuredPassword", "new-password");
    UUID userId = UUID.randomUUID();
    User existing = newAdmin(userId, "rot@example.com", UserStatus.ACTIVE, "$2a$10$old");
    AdminProfile profile = newProfile(userId, AdminTier.SUPER);

    when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(
            "rot@example.com", IdentifierType.EMAIL))
        .thenReturn(Optional.of(existing));
    when(adminProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(passwordEncoder.matches("new-password", "$2a$10$old")).thenReturn(false);
    when(passwordEncoder.encode("new-password")).thenReturn("$2a$10$new");

    bootstrap.run(null);

    assertThat(existing.getPasswordHash()).isEqualTo("$2a$10$new");
    verify(userRepository).save(existing);
  }

  @Test
  void run_existingUserNotSuperTier_reconcilesToSuper() {
    ReflectionTestUtils.setField(bootstrap, "configuredIdentifier", "rec@example.com");
    ReflectionTestUtils.setField(bootstrap, "configuredPassword", "ok-password");
    UUID userId = UUID.randomUUID();
    User existing = newAdmin(userId, "rec@example.com", UserStatus.ACTIVE, "$2a$10$ok");
    AdminProfile profile = newProfile(userId, AdminTier.MANAGER);

    when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(
            "rec@example.com", IdentifierType.EMAIL))
        .thenReturn(Optional.of(existing));
    when(adminProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(passwordEncoder.matches("ok-password", "$2a$10$ok")).thenReturn(true);

    bootstrap.run(null);

    assertThat(profile.getAdminTier()).isEqualTo(AdminTier.SUPER);
    verify(adminProfileRepository).save(profile);
    verify(userRepository).save(existing); // reconcile path saves user too
  }

  @Test
  void run_existingNonAdminUser_throwsIllegalState() {
    ReflectionTestUtils.setField(bootstrap, "configuredIdentifier", "merchant@example.com");
    ReflectionTestUtils.setField(bootstrap, "configuredPassword", "any");
    User existing = newAdmin(UUID.randomUUID(), "merchant@example.com", UserStatus.ACTIVE, "h");
    existing.setUserType(UserType.MERCHANT);

    when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(
            eq("merchant@example.com"), any(IdentifierType.class)))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> bootstrap.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-admin");
  }

  @Test
  void run_existingAdminWithoutProfile_throwsIllegalState() {
    ReflectionTestUtils.setField(bootstrap, "configuredIdentifier", "lost@example.com");
    ReflectionTestUtils.setField(bootstrap, "configuredPassword", "pw");
    UUID userId = UUID.randomUUID();
    User existing = newAdmin(userId, "lost@example.com", UserStatus.ACTIVE, "h");

    when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(
            anyString(), any(IdentifierType.class)))
        .thenReturn(Optional.of(existing));
    when(adminProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> bootstrap.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no AdminProfile");
  }

  private static User newAdmin(UUID id, String identifier, UserStatus status, String hash) {
    User u = new User();
    u.setId(id);
    u.setIdentifier(identifier);
    u.setIdentifierType(IdentifierType.EMAIL);
    u.setPasswordHash(hash);
    u.setUserType(UserType.ADMIN);
    u.setStatus(status);
    return u;
  }

  private static AdminProfile newProfile(UUID userId, AdminTier tier) {
    AdminProfile p = new AdminProfile();
    p.setId(UUID.randomUUID());
    p.setUserId(userId);
    p.setDepartment("D");
    p.setEmployeeId("EMP-" + UUID.randomUUID());
    p.setAdminTier(tier);
    return p;
  }
}
