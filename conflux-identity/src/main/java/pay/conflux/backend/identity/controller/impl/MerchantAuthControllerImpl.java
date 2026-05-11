package pay.conflux.backend.identity.controller.impl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.identity.controller.MerchantAuthController;
import pay.conflux.backend.identity.dto.LoginRequest;
import pay.conflux.backend.identity.dto.LoginResponse;
import pay.conflux.backend.identity.dto.MerchantOnboardingDto;
import pay.conflux.backend.identity.dto.RegisterMerchantRequest;
import pay.conflux.backend.identity.usecase.AuthenticateUserUseCase;
import pay.conflux.backend.identity.usecase.RegisterMerchantUseCase;

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
