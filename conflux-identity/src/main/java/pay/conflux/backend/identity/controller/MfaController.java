package pay.conflux.backend.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.identity.constant.IdentityRoutes;
import pay.conflux.backend.identity.dto.MfaDisableRequest;
import pay.conflux.backend.identity.dto.MfaEnableResponse;
import pay.conflux.backend.identity.dto.MfaVerifyRequest;

@Tag(name = "MFA", description = "TOTP-based multi-factor authentication endpoints")
@RequestMapping(IdentityRoutes.AUTH_BASE)
public interface MfaController {

  @Operation(summary = "Enable TOTP-based MFA")
  @PostMapping("/mfa/enable")
  ResponseEntity<ApiResult<MfaEnableResponse>> enable(@RequestHeader("X-User-Id") UUID userId);

  @Operation(summary = "Verify a TOTP code")
  @PostMapping("/mfa/verify")
  ResponseEntity<ApiResult<Void>> verify(
      @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody MfaVerifyRequest request);

  @Operation(summary = "Disable MFA (requires password)")
  @PostMapping("/mfa/disable")
  ResponseEntity<ApiResult<Void>> disable(
      @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody MfaDisableRequest request);
}
