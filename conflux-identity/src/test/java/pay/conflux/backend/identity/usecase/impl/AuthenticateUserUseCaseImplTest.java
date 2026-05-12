package pay.conflux.backend.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.identity.dto.LoginRequest;
import pay.conflux.backend.identity.dto.LoginResponse;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.enums.UserType;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.support.JwtTokenService;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserUseCaseImplTest {

  @Mock private UserRepository userRepository;
  @Mock private AdminProfileRepository adminProfileRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtTokenService jwtTokenService;

  @InjectMocks private AuthenticateUserUseCaseImpl useCase;

  @Test
  void execute_merchantPhoneIssuesJwtWithoutTierClaim() {
    LoginRequest request = new LoginRequest("01712345678", "password123");
    User user = newUser(UUID.randomUUID(), "01712345678", IdentifierType.PHONE, UserType.MERCHANT);

    when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(
            anyString(), any(IdentifierType.class)))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
    when(jwtTokenService.issue(user, null)).thenReturn("merchant.jwt.token");

    LoginResponse result = useCase.execute(request);

    assertThat(result.userId()).isEqualTo(user.getId());
    assertThat(result.userType()).isEqualTo(UserType.MERCHANT);
    assertThat(result.authToken()).isEqualTo("merchant.jwt.token");
  }

  @Test
  void execute_merchantEmailIssuesJwtWithoutTierClaim() {
    LoginRequest request = new LoginRequest("merchant@example.com", "password123");
    User user =
        newUser(UUID.randomUUID(), "merchant@example.com", IdentifierType.EMAIL, UserType.MERCHANT);

    when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(
            "merchant@example.com", IdentifierType.EMAIL))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
    when(jwtTokenService.issue(user, null)).thenReturn("merchant.email.token");

    LoginResponse result = useCase.execute(request);

    assertThat(result.userId()).isEqualTo(user.getId());
    assertThat(result.userType()).isEqualTo(UserType.MERCHANT);
    assertThat(result.authToken()).isEqualTo("merchant.email.token");
  }

  @Test
  void execute_adminLoadsProfileAndIssuesJwtWithTierClaim() {
    LoginRequest request = new LoginRequest("super@example.com", "password123");
    UUID adminUserId = UUID.randomUUID();
    User user = newUser(adminUserId, "super@example.com", IdentifierType.EMAIL, UserType.ADMIN);
    AdminProfile profile = newProfile(adminUserId, AdminTier.SUPER);

    when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(
            "super@example.com", IdentifierType.EMAIL))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
    when(adminProfileRepository.findByUserId(adminUserId)).thenReturn(Optional.of(profile));
    when(jwtTokenService.issue(user, profile)).thenReturn("admin.super.token");

    LoginResponse result = useCase.execute(request);

    assertThat(result.userId()).isEqualTo(adminUserId);
    assertThat(result.userType()).isEqualTo(UserType.ADMIN);
    assertThat(result.authToken()).isEqualTo("admin.super.token");
  }

  @Test
  void execute_adminWithoutProfileThrowsIllegalState() {
    LoginRequest request = new LoginRequest("admin@example.com", "password123");
    UUID adminUserId = UUID.randomUUID();
    User user = newUser(adminUserId, "admin@example.com", IdentifierType.EMAIL, UserType.ADMIN);

    when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(
            eq("admin@example.com"), any(IdentifierType.class)))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
    when(adminProfileRepository.findByUserId(adminUserId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AdminProfile");
  }

  @ParameterizedTest
  @CsvSource({
    "01712345678, wrong_password, password_mismatch",
    "unknown@example.com, password123, user_not_found"
  })
  void execute_throwsUnauthorizedForInvalidCredentials(
      String identifier, String password, String reason) {
    LoginRequest request = new LoginRequest(identifier, password);

    if ("password_mismatch".equals(reason)) {
      User user = new User();
      user.setPasswordHash("hashed");
      when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(anyString(), any()))
          .thenReturn(Optional.of(user));
      when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
    } else {
      when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(anyString(), any()))
          .thenReturn(Optional.empty());
    }

    assertThatThrownBy(() -> useCase.execute(request))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid credentials");
  }

  @Test
  void execute_throwsUnauthorizedForInactiveUser() {
    LoginRequest request = new LoginRequest("01712345678", "password123");
    User user = new User();
    user.setStatus(UserStatus.BLOCKED);
    user.setPasswordHash("hashed");

    when(userRepository.findByIdentifierAndIdentifierTypeAndDeletedFalse(anyString(), any()))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(request))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("Invalid credentials");
  }

  private static User newUser(UUID id, String identifier, IdentifierType type, UserType userType) {
    User u = new User();
    u.setId(id);
    u.setIdentifier(identifier);
    u.setIdentifierType(type);
    u.setPasswordHash("hashed_password");
    u.setStatus(UserStatus.ACTIVE);
    u.setUserType(userType);
    return u;
  }

  private static AdminProfile newProfile(UUID userId, AdminTier tier) {
    AdminProfile p = new AdminProfile();
    p.setId(UUID.randomUUID());
    p.setUserId(userId);
    p.setDepartment("Platform");
    p.setEmployeeId("EMP-" + UUID.randomUUID());
    p.setAdminTier(tier);
    return p;
  }
}
