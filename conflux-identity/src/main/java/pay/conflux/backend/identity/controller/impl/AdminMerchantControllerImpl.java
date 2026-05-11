package pay.conflux.backend.identity.controller.impl;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.identity.constant.IdentityRoutes;
import pay.conflux.backend.identity.controller.AdminMerchantController;
import pay.conflux.backend.identity.dto.BlockUserRequest;
import pay.conflux.backend.identity.dto.MerchantSummaryDto;
import pay.conflux.backend.identity.dto.RejectMerchantRequest;
import pay.conflux.backend.identity.enums.OnboardingStatus;
import pay.conflux.backend.identity.usecase.BlockUserUseCase;
import pay.conflux.backend.identity.usecase.GetMerchantProfilesUseCase;
import pay.conflux.backend.identity.usecase.RejectMerchantUseCase;
import pay.conflux.backend.identity.usecase.UnblockUserUseCase;
import pay.conflux.backend.identity.usecase.VerifyMerchantUseCase;

@RestController
@RequiredArgsConstructor
public class AdminMerchantControllerImpl implements AdminMerchantController {

  private final GetMerchantProfilesUseCase getMerchantProfilesUseCase;
  private final VerifyMerchantUseCase verifyMerchantUseCase;
  private final RejectMerchantUseCase rejectMerchantUseCase;
  private final BlockUserUseCase blockUserUseCase;
  private final UnblockUserUseCase unblockUserUseCase;

  @Override
  @GetMapping(IdentityRoutes.ADMIN_MERCHANTS)
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<List<MerchantSummaryDto>>> listMerchants(
      @RequestParam(required = false) OnboardingStatus status,
      @RequestParam(required = false) String search,
      Pageable pageable) {
    Page<MerchantSummaryDto> page = getMerchantProfilesUseCase.execute(status, search, pageable);
    return ApiResult.ok(page);
  }

  @Override
  @PostMapping(IdentityRoutes.ADMIN_MERCHANTS_VERIFY)
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<Void>> verifyMerchant(@PathVariable UUID id) {
    verifyMerchantUseCase.execute(id);
    return ApiResult.ok();
  }

  @Override
  @PostMapping(IdentityRoutes.ADMIN_MERCHANTS_REJECT)
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<Void>> rejectMerchant(
      @PathVariable UUID id, @Valid @RequestBody RejectMerchantRequest request) {
    rejectMerchantUseCase.execute(id, request);
    return ApiResult.ok();
  }

  @Override
  @PostMapping(IdentityRoutes.ADMIN_USERS_BLOCK)
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<Void>> blockUser(
      @PathVariable UUID id, @Valid @RequestBody(required = false) BlockUserRequest request) {
    blockUserUseCase.execute(id, request);
    return ApiResult.ok();
  }

  @Override
  @PostMapping(IdentityRoutes.ADMIN_USERS_UNBLOCK)
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<Void>> unblockUser(@PathVariable UUID id) {
    unblockUserUseCase.execute(id);
    return ApiResult.ok();
  }
}
