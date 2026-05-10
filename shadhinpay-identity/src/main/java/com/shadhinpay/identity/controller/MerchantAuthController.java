package com.shadhinpay.identity.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.identity.constant.IdentityRoutes;
import com.shadhinpay.identity.dto.LoginRequest;
import com.shadhinpay.identity.dto.LoginResponse;
import com.shadhinpay.identity.dto.MerchantOnboardingDto;
import com.shadhinpay.identity.dto.RegisterMerchantRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(
    name = "Merchant Authentication",
    description = "Endpoints for merchant registration and login")
public interface MerchantAuthController {

  @Operation(summary = "Register a new merchant")
  @PostMapping(IdentityRoutes.MERCHANT_REGISTER)
  ResponseEntity<ApiResult<MerchantOnboardingDto>> register(
      @Valid @RequestBody RegisterMerchantRequest request);

  @Operation(summary = "Login to the system")
  @PostMapping(IdentityRoutes.AUTH_LOGIN)
  ResponseEntity<ApiResult<LoginResponse>> login(@Valid @RequestBody LoginRequest request);
}
