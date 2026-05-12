package pay.conflux.backend.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pay.conflux.backend.common.constant.Routes;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.identity.dto.LoginRequest;
import pay.conflux.backend.identity.dto.LoginResponse;
import pay.conflux.backend.identity.dto.MerchantOnboardingDto;
import pay.conflux.backend.identity.dto.RegisterMerchantRequest;

@Tag(
    name = "Merchant Authentication",
    description = "Endpoints for merchant registration and login")
@RequestMapping(Routes.V1.BASE)
public interface MerchantAuthController {

  @Operation(summary = "Register a new merchant")
  @PostMapping("/merchant/register")
  ResponseEntity<ApiResult<MerchantOnboardingDto>> register(RegisterMerchantRequest request);

  @Operation(summary = "Login to the system")
  @PostMapping("/auth/login")
  ResponseEntity<ApiResult<LoginResponse>> login(LoginRequest request);
}
