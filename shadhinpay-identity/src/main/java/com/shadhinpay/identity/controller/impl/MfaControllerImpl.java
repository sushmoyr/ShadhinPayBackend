package com.shadhinpay.identity.controller.impl;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.identity.controller.MfaController;
import com.shadhinpay.identity.dto.MfaDisableRequest;
import com.shadhinpay.identity.dto.MfaEnableResponse;
import com.shadhinpay.identity.dto.MfaVerifyRequest;
import com.shadhinpay.identity.usecase.DisableMfaUseCase;
import com.shadhinpay.identity.usecase.EnableMfaUseCase;
import com.shadhinpay.identity.usecase.VerifyMfaUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MfaControllerImpl implements MfaController {

  private final EnableMfaUseCase enableMfaUseCase;
  private final VerifyMfaUseCase verifyMfaUseCase;
  private final DisableMfaUseCase disableMfaUseCase;

  @Override
  public ResponseEntity<ApiResult<MfaEnableResponse>> enable(UUID userId) {
    return ApiResult.ok(enableMfaUseCase.execute(userId));
  }

  @Override
  public ResponseEntity<ApiResult<Void>> verify(UUID userId, MfaVerifyRequest request) {
    verifyMfaUseCase.execute(userId, request.code());
    return ApiResult.ok();
  }

  @Override
  public ResponseEntity<ApiResult<Void>> disable(UUID userId, MfaDisableRequest request) {
    disableMfaUseCase.execute(userId, request.password());
    return ApiResult.ok();
  }
}
