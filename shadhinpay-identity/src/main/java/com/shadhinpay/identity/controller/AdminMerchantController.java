package com.shadhinpay.identity.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.identity.constant.IdentityRoutes;
import com.shadhinpay.identity.dto.BlockUserRequest;
import com.shadhinpay.identity.dto.MerchantSummaryDto;
import com.shadhinpay.identity.dto.RejectMerchantRequest;
import com.shadhinpay.identity.entity.enums.OnboardingStatus;
import com.shadhinpay.identity.usecase.BlockUserUseCase;
import com.shadhinpay.identity.usecase.GetMerchantProfilesUseCase;
import com.shadhinpay.identity.usecase.RejectMerchantUseCase;
import com.shadhinpay.identity.usecase.UnblockUserUseCase;
import com.shadhinpay.identity.usecase.VerifyMerchantUseCase;
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

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGER')")
public class AdminMerchantController {

  private final GetMerchantProfilesUseCase getMerchantProfilesUseCase;
  private final VerifyMerchantUseCase verifyMerchantUseCase;
  private final RejectMerchantUseCase rejectMerchantUseCase;
  private final BlockUserUseCase blockUserUseCase;
  private final UnblockUserUseCase unblockUserUseCase;

  @GetMapping(IdentityRoutes.ADMIN_MERCHANTS)
  public ResponseEntity<ApiResult<List<MerchantSummaryDto>>> listMerchants(
      @RequestParam(required = false) OnboardingStatus status,
      @RequestParam(required = false) String search,
      Pageable pageable) {
    Page<MerchantSummaryDto> page = getMerchantProfilesUseCase.execute(status, search, pageable);
    return ApiResult.ok(page);
  }

  @PostMapping(IdentityRoutes.ADMIN_MERCHANTS_VERIFY)
  public ResponseEntity<ApiResult<Void>> verifyMerchant(@PathVariable UUID id) {
    verifyMerchantUseCase.execute(id);
    return ApiResult.ok();
  }

  @PostMapping(IdentityRoutes.ADMIN_MERCHANTS_REJECT)
  public ResponseEntity<ApiResult<Void>> rejectMerchant(
      @PathVariable UUID id, @Valid @RequestBody RejectMerchantRequest request) {
    rejectMerchantUseCase.execute(id, request);
    return ApiResult.ok();
  }

  @PostMapping(IdentityRoutes.ADMIN_USERS_BLOCK)
  public ResponseEntity<ApiResult<Void>> blockUser(
      @PathVariable UUID id, @Valid @RequestBody(required = false) BlockUserRequest request) {
    blockUserUseCase.execute(id, request);
    return ApiResult.ok();
  }

  @PostMapping(IdentityRoutes.ADMIN_USERS_UNBLOCK)
  public ResponseEntity<ApiResult<Void>> unblockUser(@PathVariable UUID id) {
    unblockUserUseCase.execute(id);
    return ApiResult.ok();
  }
}
