package pay.conflux.backend.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pay.conflux.backend.common.error.DuplicateResourceException;
import pay.conflux.backend.identity.dto.AdminProfileDto;
import pay.conflux.backend.identity.dto.CreateAdminRequest;
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
class CreateAdminUseCaseImplTest {

  @Mock private UserRepository userRepository;
  @Mock private AdminProfileRepository adminProfileRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private AdminProfileMapper adminProfileMapper;

  @InjectMocks private CreateAdminUseCaseImpl useCase;

  @Test
  void execute_happyPathEmailIdentifier() {
    CreateAdminRequest request =
        new CreateAdminRequest(
            "newadmin@example.com", "password123", "Risk", "EMP-001", AdminTier.MANAGER);

    when(userRepository.existsByIdentifierAndIdentifierTypeAndDeletedFalse(
            "newadmin@example.com", IdentifierType.EMAIL))
        .thenReturn(false);
    when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedvalue");
    when(adminProfileMapper.toDto(any(User.class), any(AdminProfile.class)))
        .thenReturn(
            new AdminProfileDto(
                null,
                null,
                "newadmin@example.com",
                IdentifierType.EMAIL,
                UserStatus.ACTIVE,
                "Risk",
                "EMP-001",
                AdminTier.MANAGER));

    AdminProfileDto result = useCase.execute(request);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getUserType()).isEqualTo(UserType.ADMIN);
    assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashedvalue");

    ArgumentCaptor<AdminProfile> profileCaptor = ArgumentCaptor.forClass(AdminProfile.class);
    verify(adminProfileRepository).save(profileCaptor.capture());
    assertThat(profileCaptor.getValue().getAdminTier()).isEqualTo(AdminTier.MANAGER);
    assertThat(profileCaptor.getValue().getEmployeeId()).isEqualTo("EMP-001");

    assertThat(result.adminTier()).isEqualTo(AdminTier.MANAGER);
  }

  @Test
  void execute_duplicateIdentifierRejected() {
    CreateAdminRequest request =
        new CreateAdminRequest(
            "dup@example.com", "password123", "Risk", "EMP-002", AdminTier.VIEWER);
    when(userRepository.existsByIdentifierAndIdentifierTypeAndDeletedFalse(
            anyString(), any(IdentifierType.class)))
        .thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(request))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessageContaining("dup@example.com");
  }
}
