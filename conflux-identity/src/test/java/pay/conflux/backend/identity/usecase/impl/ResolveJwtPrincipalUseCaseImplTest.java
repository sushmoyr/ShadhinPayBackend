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
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.common.security.AuthenticatedPrincipal;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.enums.UserType;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.identity.support.JwtClaims;
import pay.conflux.backend.identity.support.JwtTokenService;
import pay.conflux.backend.identity.usecase.ResolveJwtPrincipalUseCase;

@ExtendWith(MockitoExtension.class)
class ResolveJwtPrincipalUseCaseImplTest {

  private static final String TOKEN = "aaaa.bbbb.cccc";

  @Mock private JwtTokenService jwtTokenService;
  @Mock private UserRepository userRepository;
  @Mock private AdminProfileRepository adminProfileRepository;

  @InjectMocks private ResolveJwtPrincipalUseCaseImpl useCase;

  @Test
  void execute_merchant_returnsMerchantUserType_andNullTier() {
    UUID userId = UUID.randomUUID();
    when(jwtTokenService.parse(TOKEN)).thenReturn(new JwtClaims(userId, UserType.MERCHANT, null));
    when(userRepository.findById(userId)).thenReturn(Optional.of(merchant(userId)));

    ResolveJwtPrincipalUseCase.Resolved resolved = useCase.execute(TOKEN);

    assertThat(resolved.userId()).isEqualTo(userId);
    assertThat(resolved.userType()).isEqualTo(AuthenticatedPrincipal.UserType.MERCHANT);
    assertThat(resolved.adminTierName()).isNull();
  }

  @Test
  void execute_admin_returnsAdminTierNameAsString() {
    UUID userId = UUID.randomUUID();
    when(jwtTokenService.parse(TOKEN))
        .thenReturn(new JwtClaims(userId, UserType.ADMIN, AdminTier.MANAGER));
    when(userRepository.findById(userId)).thenReturn(Optional.of(admin(userId)));
    when(adminProfileRepository.findByUserId(userId))
        .thenReturn(Optional.of(adminProfile(userId, AdminTier.MANAGER)));

    ResolveJwtPrincipalUseCase.Resolved resolved = useCase.execute(TOKEN);

    assertThat(resolved.userType()).isEqualTo(AuthenticatedPrincipal.UserType.ADMIN);
    assertThat(resolved.adminTierName()).isEqualTo("MANAGER");
  }

  @Test
  void execute_userNotFound_throwsUnauthorized() {
    UUID userId = UUID.randomUUID();
    when(jwtTokenService.parse(TOKEN)).thenReturn(new JwtClaims(userId, UserType.MERCHANT, null));
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(TOKEN))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("not active");
  }

  @Test
  void execute_userNotActive_throwsUnauthorized() {
    UUID userId = UUID.randomUUID();
    User blocked = merchant(userId);
    blocked.setStatus(UserStatus.BLOCKED);
    when(jwtTokenService.parse(TOKEN)).thenReturn(new JwtClaims(userId, UserType.MERCHANT, null));
    when(userRepository.findById(userId)).thenReturn(Optional.of(blocked));

    assertThatThrownBy(() -> useCase.execute(TOKEN))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("not active");
  }

  @Test
  void execute_adminMissingProfile_throwsUnauthorized() {
    UUID userId = UUID.randomUUID();
    when(jwtTokenService.parse(TOKEN))
        .thenReturn(new JwtClaims(userId, UserType.ADMIN, AdminTier.SUPER));
    when(userRepository.findById(userId)).thenReturn(Optional.of(admin(userId)));
    when(adminProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(TOKEN))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Admin profile not found");
  }

  @Test
  void execute_jwtParseFailure_propagatesUnauthorized() {
    when(jwtTokenService.parse(TOKEN)).thenThrow(new UnauthorizedException("JWT is expired"));

    assertThatThrownBy(() -> useCase.execute(TOKEN))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("expired");
  }

  private static User merchant(UUID id) {
    User u = new User();
    u.setId(id);
    u.setIdentifier("m@example.com");
    u.setIdentifierType(IdentifierType.EMAIL);
    u.setUserType(UserType.MERCHANT);
    u.setStatus(UserStatus.ACTIVE);
    u.setPasswordHash("h");
    return u;
  }

  private static User admin(UUID id) {
    User u = new User();
    u.setId(id);
    u.setIdentifier("a@example.com");
    u.setIdentifierType(IdentifierType.EMAIL);
    u.setUserType(UserType.ADMIN);
    u.setStatus(UserStatus.ACTIVE);
    u.setPasswordHash("h");
    return u;
  }

  private static AdminProfile adminProfile(UUID userId, AdminTier tier) {
    AdminProfile p = new AdminProfile();
    p.setId(UUID.randomUUID());
    p.setUserId(userId);
    p.setDepartment("Ops");
    p.setEmployeeId("EMP-1");
    p.setAdminTier(tier);
    return p;
  }
}
