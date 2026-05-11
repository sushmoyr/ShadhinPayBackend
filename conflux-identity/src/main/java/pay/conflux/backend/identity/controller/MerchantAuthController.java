package pay.conflux.backend.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.identity.constant.IdentityRoutes;
import pay.conflux.backend.identity.dto.LoginRequest;
import pay.conflux.backend.identity.dto.LoginResponse;
import pay.conflux.backend.identity.dto.MerchantOnboardingDto;
import pay.conflux.backend.identity.dto.RegisterMerchantRequest;

@Tag(
    name = "Merchant Authentication",
    description = "Endpoints for merchant registration and login")
public interface MerchantAuthController {

  @Operation(summary = "Register a new merchant")
  @PostMapping(IdentityRoutes.MERCHANT_REGISTER)
  ResponseEntity<ApiResult<MerchantOnboardingDto>> register(RegisterMerchantRequest request);

  @Operation(summary = "Login to the system")
  @PostMapping(IdentityRoutes.AUTH_LOGIN)
  ResponseEntity<ApiResult<LoginResponse>> login(LoginRequest request);
}
