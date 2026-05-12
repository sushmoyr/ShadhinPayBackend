package pay.conflux.backend.identity.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import pay.conflux.backend.common.dto.PaginationRequest;
import pay.conflux.backend.identity.dto.AdminProfileSummaryDto;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.mapper.AdminProfileMapper;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ListAdminsUseCaseImplTest {

  @Mock private AdminProfileRepository adminProfileRepository;
  @Mock private UserRepository userRepository;
  @Mock private AdminProfileMapper adminProfileMapper;

  @InjectMocks private ListAdminsUseCaseImpl useCase;

  @Test
  void execute_withoutFilterUsesFindAll() {
    PaginationRequest pagination = new PaginationRequest();
    UUID userId = UUID.randomUUID();
    AdminProfile profile = newProfile(userId, AdminTier.MANAGER);
    User user = newUser(userId);

    when(adminProfileRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(profile)));
    when(userRepository.findAllById(List.of(userId))).thenReturn(List.of(user));
    when(adminProfileMapper.toSummaryDto(user, profile))
        .thenReturn(
            new AdminProfileSummaryDto(
                userId,
                profile.getId(),
                "x@example.com",
                UserStatus.ACTIVE,
                "EMP-A",
                AdminTier.MANAGER));

    Page<AdminProfileSummaryDto> result = useCase.execute(pagination, null);

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  void execute_withTierFilterUsesFindByAdminTier() {
    PaginationRequest pagination = new PaginationRequest();
    UUID userId = UUID.randomUUID();
    AdminProfile profile = newProfile(userId, AdminTier.SUPER);
    User user = newUser(userId);

    when(adminProfileRepository.findByAdminTier(eq(AdminTier.SUPER), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(profile)));
    when(userRepository.findAllById(List.of(userId))).thenReturn(List.of(user));
    when(adminProfileMapper.toSummaryDto(user, profile))
        .thenReturn(
            new AdminProfileSummaryDto(
                userId,
                profile.getId(),
                "s@example.com",
                UserStatus.ACTIVE,
                "EMP-S",
                AdminTier.SUPER));

    Page<AdminProfileSummaryDto> result = useCase.execute(pagination, AdminTier.SUPER);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).adminTier()).isEqualTo(AdminTier.SUPER);
    verify(adminProfileRepository).findByAdminTier(eq(AdminTier.SUPER), any(Pageable.class));
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

  private static User newUser(UUID id) {
    User u = new User();
    u.setId(id);
    u.setIdentifier("x@example.com");
    return u;
  }
}
