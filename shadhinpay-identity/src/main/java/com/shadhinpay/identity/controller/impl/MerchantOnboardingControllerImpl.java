package com.shadhinpay.identity.controller.impl;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.identity.constant.IdentityRoutes;
import com.shadhinpay.identity.controller.MerchantOnboardingController;
import com.shadhinpay.identity.dto.KycSubmissionRequest;
import com.shadhinpay.identity.dto.MerchantOnboardingDto;
import com.shadhinpay.identity.usecase.GetMerchantProfileUseCase;
import com.shadhinpay.identity.usecase.SubmitKycDocumentsUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MerchantOnboardingControllerImpl implements MerchantOnboardingController {

  private final SubmitKycDocumentsUseCase submitKycDocumentsUseCase;
  private final GetMerchantProfileUseCase getMerchantProfileUseCase;

  // TODO(wave-b): replace @RequestHeader("X-User-Id") with SecurityContextHolder principal.
  @Override
  @PostMapping(IdentityRoutes.MERCHANT_KYC)
  public ResponseEntity<ApiResult<MerchantOnboardingDto>> submitKyc(
      @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody KycSubmissionRequest request) {
    return ApiResult.ok(submitKycDocumentsUseCase.execute(userId, request));
  }

  // TODO(wave-b): replace @RequestHeader("X-User-Id") with SecurityContextHolder principal.
  @Override
  @GetMapping(IdentityRoutes.MERCHANT_ME)
  public ResponseEntity<ApiResult<MerchantOnboardingDto>> getMe(
      @RequestHeader("X-User-Id") UUID userId) {
    return ApiResult.ok(getMerchantProfileUseCase.execute(userId));
  }
}
