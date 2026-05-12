package pay.conflux.backend.identity.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.common.dto.PaginationRequest;
import pay.conflux.backend.identity.constant.IdentityRoutes;
import pay.conflux.backend.identity.dto.AdminProfileDto;
import pay.conflux.backend.identity.dto.AdminProfileSummaryDto;
import pay.conflux.backend.identity.dto.CreateAdminRequest;
import pay.conflux.backend.identity.dto.UpdateAdminTierRequest;
import pay.conflux.backend.identity.enums.AdminTier;

@Tag(name = "Admin - Admins", description = "SUPER-admin management of admin tier and lifecycle")
@RequestMapping(IdentityRoutes.ADMIN_BASE)
public interface AdminManagementController {

  @GetMapping("/admins")
  ResponseEntity<ApiResult<List<AdminProfileSummaryDto>>> listAdmins(
      AdminTier tier, PaginationRequest pagination);

  @PostMapping("/admins")
  ResponseEntity<ApiResult<AdminProfileDto>> createAdmin(CreateAdminRequest body);

  @PatchMapping("/admins/{id}/tier")
  ResponseEntity<ApiResult<AdminProfileDto>> updateAdminTier(UUID id, UpdateAdminTierRequest body);

  @PostMapping("/admins/{id}/disable")
  ResponseEntity<ApiResult<Void>> disableAdmin(UUID id);

  @GetMapping("/me")
  ResponseEntity<ApiResult<AdminProfileDto>> me();
}
