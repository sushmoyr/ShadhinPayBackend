package com.shadhinpay.identity.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.identity.constant.IdentityRoutes;
import com.shadhinpay.identity.dto.MfaDisableRequest;
import com.shadhinpay.identity.dto.MfaEnableResponse;
import com.shadhinpay.identity.dto.MfaVerifyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "MFA", description = "TOTP-based multi-factor authentication endpoints")
public interface MfaController {

  @Operation(summary = "Enable TOTP-based MFA")
  @PostMapping(IdentityRoutes.AUTH_MFA_ENABLE)
  ResponseEntity<ApiResult<MfaEnableResponse>> enable(@RequestHeader("X-User-Id") UUID userId);

  @Operation(summary = "Verify a TOTP code")
  @PostMapping(IdentityRoutes.AUTH_MFA_VERIFY)
  ResponseEntity<ApiResult<Void>> verify(
      @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody MfaVerifyRequest request);

  @Operation(summary = "Disable MFA (requires password)")
  @PostMapping(IdentityRoutes.AUTH_MFA_DISABLE)
  ResponseEntity<ApiResult<Void>> disable(
      @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody MfaDisableRequest request);
}
