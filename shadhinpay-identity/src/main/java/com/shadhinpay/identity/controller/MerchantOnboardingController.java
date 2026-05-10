package com.shadhinpay.identity.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.identity.constant.IdentityRoutes;
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
public class MerchantOnboardingController {

  private final SubmitKycDocumentsUseCase submitKycDocumentsUseCase;
  private final GetMerchantProfileUseCase getMerchantProfileUseCase;

  // Assuming X-User-Id is passed by the gateway. If not, it should be extracted from the auth
  // context.
  @PostMapping(IdentityRoutes.MERCHANT_KYC)
  public ResponseEntity<ApiResult<MerchantOnboardingDto>> submitKyc(
      @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody KycSubmissionRequest request) {
    MerchantOnboardingDto dto = submitKycDocumentsUseCase.execute(userId, request);
    return ApiResult.ok(dto);
  }

  @GetMapping(IdentityRoutes.MERCHANT_ME)
  public ResponseEntity<ApiResult<MerchantOnboardingDto>> getMe(
      @RequestHeader("X-User-Id") UUID userId) {
    MerchantOnboardingDto dto = getMerchantProfileUseCase.execute(userId);
    return ApiResult.ok(dto);
  }
}
