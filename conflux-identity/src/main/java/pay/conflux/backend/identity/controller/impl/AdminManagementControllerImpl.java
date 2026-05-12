package pay.conflux.backend.identity.controller.impl;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.common.dto.PaginationRequest;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.common.security.SecurityUtils;
import pay.conflux.backend.identity.controller.AdminManagementController;
import pay.conflux.backend.identity.dto.AdminProfileDto;
import pay.conflux.backend.identity.dto.AdminProfileSummaryDto;
import pay.conflux.backend.identity.dto.CreateAdminRequest;
import pay.conflux.backend.identity.dto.UpdateAdminTierRequest;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.usecase.CreateAdminUseCase;
import pay.conflux.backend.identity.usecase.DisableAdminUseCase;
import pay.conflux.backend.identity.usecase.ListAdminsUseCase;
import pay.conflux.backend.identity.usecase.UpdateAdminTierUseCase;

@RestController
@RequiredArgsConstructor
public class AdminManagementControllerImpl implements AdminManagementController {

  private final ListAdminsUseCase listAdminsUseCase;
  private final CreateAdminUseCase createAdminUseCase;
  private final UpdateAdminTierUseCase updateAdminTierUseCase;
  private final DisableAdminUseCase disableAdminUseCase;

  @Override
  @PreAuthorize("hasAuthority('ADMIN_VIEWER')")
  public ResponseEntity<ApiResult<List<AdminProfileSummaryDto>>> listAdmins(
      @RequestParam(required = false) AdminTier tier, PaginationRequest pagination) {
    Page<AdminProfileSummaryDto> page = listAdminsUseCase.execute(pagination, tier);
    return ApiResult.ok(page);
  }

  @Override
  @PreAuthorize("hasAuthority('SUPER_ADMIN')")
  public ResponseEntity<ApiResult<AdminProfileDto>> createAdmin(
      @Valid @RequestBody CreateAdminRequest body) {
    return ApiResult.created(createAdminUseCase.execute(body));
  }

  @Override
  @PreAuthorize("hasAuthority('SUPER_ADMIN')")
  public ResponseEntity<ApiResult<AdminProfileDto>> updateAdminTier(
      @PathVariable UUID id, @Valid @RequestBody UpdateAdminTierRequest body) {
    return ApiResult.ok(updateAdminTierUseCase.execute(id, body.newTier()));
  }

  @Override
  @PreAuthorize("hasAuthority('SUPER_ADMIN')")
  public ResponseEntity<ApiResult<Void>> disableAdmin(@PathVariable UUID id) {
    UUID callerUserId =
        SecurityUtils.currentAdminId()
            .orElseThrow(() -> new UnauthorizedException("No admin context"));
    disableAdminUseCase.execute(id, callerUserId);
    return ApiResult.ok();
  }

  /**
   * TODO(wave-d 1c): replace this 501 stub with a real implementation backed by {@code
   * GetAdminProfileUseCase} + {@code SecurityUtils.currentAdminId()} once {@code
   * JwtAuthorizationFilter} populates the principal.
   */
  @Override
  @PreAuthorize("hasAuthority('ADMIN_VIEWER')")
  public ResponseEntity<ApiResult<AdminProfileDto>> me() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }
}
