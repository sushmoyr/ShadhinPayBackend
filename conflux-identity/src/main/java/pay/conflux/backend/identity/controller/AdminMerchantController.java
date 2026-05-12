package pay.conflux.backend.identity.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.identity.constant.IdentityRoutes;
import pay.conflux.backend.identity.dto.BlockUserRequest;
import pay.conflux.backend.identity.dto.MerchantSummaryDto;
import pay.conflux.backend.identity.dto.RejectMerchantRequest;
import pay.conflux.backend.identity.enums.OnboardingStatus;

@Tag(name = "Admin - Merchants", description = "Administrative merchant management endpoints")
@RequestMapping(IdentityRoutes.ADMIN_BASE)
public interface AdminMerchantController {

  @GetMapping("/merchants")
  ResponseEntity<ApiResult<List<MerchantSummaryDto>>> listMerchants(
      OnboardingStatus status, String search, Pageable pageable);

  @PostMapping("/merchants/{id}/verify")
  ResponseEntity<ApiResult<Void>> verifyMerchant(UUID id);

  @PostMapping("/merchants/{id}/reject")
  ResponseEntity<ApiResult<Void>> rejectMerchant(UUID id, RejectMerchantRequest request);

  @PostMapping("/users/{id}/block")
  ResponseEntity<ApiResult<Void>> blockUser(UUID id, BlockUserRequest request);

  @PostMapping("/users/{id}/unblock")
  ResponseEntity<ApiResult<Void>> unblockUser(UUID id);
}
