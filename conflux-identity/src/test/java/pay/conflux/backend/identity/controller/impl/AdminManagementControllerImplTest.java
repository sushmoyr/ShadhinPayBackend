package pay.conflux.backend.identity.controller.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.common.dto.PaginationRequest;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.common.security.AuthenticatedPrincipal;
import pay.conflux.backend.identity.dto.AdminProfileDto;
import pay.conflux.backend.identity.dto.AdminProfileSummaryDto;
import pay.conflux.backend.identity.dto.CreateAdminRequest;
import pay.conflux.backend.identity.dto.UpdateAdminTierRequest;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.usecase.CreateAdminUseCase;
import pay.conflux.backend.identity.usecase.DisableAdminUseCase;
import pay.conflux.backend.identity.usecase.ListAdminsUseCase;
import pay.conflux.backend.identity.usecase.UpdateAdminTierUseCase;

@ExtendWith(MockitoExtension.class)
class AdminManagementControllerImplTest {

  @Mock private ListAdminsUseCase listAdminsUseCase;
  @Mock private CreateAdminUseCase createAdminUseCase;
  @Mock private UpdateAdminTierUseCase updateAdminTierUseCase;
  @Mock private DisableAdminUseCase disableAdminUseCase;

  @InjectMocks private AdminManagementControllerImpl controller;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void listAdmins_delegatesAndReturnsOkEnvelope() {
    PaginationRequest pagination = new PaginationRequest();
    AdminProfileSummaryDto summary =
        new AdminProfileSummaryDto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "x@example.com",
            UserStatus.ACTIVE,
            "EMP-A",
            AdminTier.MANAGER);
    Page<AdminProfileSummaryDto> page = new PageImpl<>(List.of(summary));
    when(listAdminsUseCase.execute(pagination, null)).thenReturn(page);

    ResponseEntity<ApiResult<List<AdminProfileSummaryDto>>> response =
        controller.listAdmins(null, pagination);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().data()).containsExactly(summary);
  }

  @Test
  void createAdmin_delegatesAndReturns201() {
    CreateAdminRequest req =
        new CreateAdminRequest("a@example.com", "password123", "Risk", "EMP-1", AdminTier.MANAGER);
    AdminProfileDto dto =
        new AdminProfileDto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "a@example.com",
            IdentifierType.EMAIL,
            UserStatus.ACTIVE,
            "Risk",
            "EMP-1",
            AdminTier.MANAGER);
    when(createAdminUseCase.execute(req)).thenReturn(dto);

    ResponseEntity<ApiResult<AdminProfileDto>> response = controller.createAdmin(req);

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody().data()).isEqualTo(dto);
  }

  @Test
  void updateAdminTier_delegatesToUseCase() {
    UUID id = UUID.randomUUID();
    UpdateAdminTierRequest body = new UpdateAdminTierRequest(AdminTier.SUPER);
    AdminProfileDto dto =
        new AdminProfileDto(
            id,
            UUID.randomUUID(),
            "a@example.com",
            IdentifierType.EMAIL,
            UserStatus.ACTIVE,
            "Risk",
            "EMP-1",
            AdminTier.SUPER);
    when(updateAdminTierUseCase.execute(id, AdminTier.SUPER)).thenReturn(dto);

    ResponseEntity<ApiResult<AdminProfileDto>> response = controller.updateAdminTier(id, body);

    assertThat(response.getBody().data().adminTier()).isEqualTo(AdminTier.SUPER);
    verify(updateAdminTierUseCase).execute(id, AdminTier.SUPER);
  }

  @Test
  void disableAdmin_passesCallerIdFromSecurityContext() {
    UUID targetId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();
    setAdminPrincipal(callerId);

    ResponseEntity<ApiResult<Void>> response = controller.disableAdmin(targetId);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    verify(disableAdminUseCase).execute(targetId, callerId);
  }

  @Test
  void disableAdmin_throwsUnauthorizedWhenNoAdminContext() {
    UUID targetId = UUID.randomUUID();

    assertThatThrownBy(() -> controller.disableAdmin(targetId))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("No admin context");

    verify(disableAdminUseCase, org.mockito.Mockito.never()).execute(any(), any());
  }

  @Test
  void me_returnsNotImplementedStubFor1b() {
    ResponseEntity<ApiResult<AdminProfileDto>> response = controller.me();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
  }

  private static void setAdminPrincipal(UUID userId) {
    AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal(userId, AuthenticatedPrincipal.UserType.ADMIN, null, null, null);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }
}
