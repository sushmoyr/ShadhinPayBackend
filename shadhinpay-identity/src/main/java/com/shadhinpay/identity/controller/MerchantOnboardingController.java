package com.shadhinpay.identity.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.identity.constant.IdentityRoutes;
import com.shadhinpay.identity.dto.KycSubmissionRequest;
import com.shadhinpay.identity.dto.MerchantOnboardingDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Tag(name = "Merchant Onboarding", description = "KYC submission and onboarding status endpoints")
public interface MerchantOnboardingController {

  @PostMapping(IdentityRoutes.MERCHANT_KYC)
  ResponseEntity<ApiResult<MerchantOnboardingDto>> submitKyc(
      UUID userId, KycSubmissionRequest request);

  @GetMapping(IdentityRoutes.MERCHANT_ME)
  ResponseEntity<ApiResult<MerchantOnboardingDto>> getMe(UUID userId);
}
