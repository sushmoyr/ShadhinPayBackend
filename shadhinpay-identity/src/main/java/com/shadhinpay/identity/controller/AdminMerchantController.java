package com.shadhinpay.identity.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.identity.constant.IdentityRoutes;
import com.shadhinpay.identity.dto.BlockUserRequest;
import com.shadhinpay.identity.dto.MerchantSummaryDto;
import com.shadhinpay.identity.dto.RejectMerchantRequest;
import com.shadhinpay.identity.enums.OnboardingStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Tag(name = "Admin - Merchants", description = "Administrative merchant management endpoints")
public interface AdminMerchantController {

  @GetMapping(IdentityRoutes.ADMIN_MERCHANTS)
  ResponseEntity<ApiResult<List<MerchantSummaryDto>>> listMerchants(
      OnboardingStatus status, String search, Pageable pageable);

  @PostMapping(IdentityRoutes.ADMIN_MERCHANTS_VERIFY)
  ResponseEntity<ApiResult<Void>> verifyMerchant(UUID id);

  @PostMapping(IdentityRoutes.ADMIN_MERCHANTS_REJECT)
  ResponseEntity<ApiResult<Void>> rejectMerchant(UUID id, RejectMerchantRequest request);

  @PostMapping(IdentityRoutes.ADMIN_USERS_BLOCK)
  ResponseEntity<ApiResult<Void>> blockUser(UUID id, BlockUserRequest request);

  @PostMapping(IdentityRoutes.ADMIN_USERS_UNBLOCK)
  ResponseEntity<ApiResult<Void>> unblockUser(UUID id);
}
