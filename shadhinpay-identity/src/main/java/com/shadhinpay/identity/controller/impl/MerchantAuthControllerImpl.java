package com.shadhinpay.identity.controller.impl;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.identity.controller.MerchantAuthController;
import com.shadhinpay.identity.dto.LoginRequest;
import com.shadhinpay.identity.dto.LoginResponse;
import com.shadhinpay.identity.dto.MerchantOnboardingDto;
import com.shadhinpay.identity.dto.RegisterMerchantRequest;
import com.shadhinpay.identity.usecase.AuthenticateUserUseCase;
import com.shadhinpay.identity.usecase.RegisterMerchantUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MerchantAuthControllerImpl implements MerchantAuthController {

  private final RegisterMerchantUseCase registerMerchantUseCase;
  private final AuthenticateUserUseCase authenticateUserUseCase;

  @Override
  public ResponseEntity<ApiResult<MerchantOnboardingDto>> register(
      @Valid @RequestBody RegisterMerchantRequest request) {
    return ApiResult.created(registerMerchantUseCase.execute(request));
  }

  @Override
  public ResponseEntity<ApiResult<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
    return ApiResult.ok(authenticateUserUseCase.execute(request));
  }
}
