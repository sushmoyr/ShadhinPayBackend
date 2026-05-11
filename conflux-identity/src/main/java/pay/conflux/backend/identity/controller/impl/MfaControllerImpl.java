package pay.conflux.backend.identity.controller.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.identity.controller.MfaController;
import pay.conflux.backend.identity.dto.MfaDisableRequest;
import pay.conflux.backend.identity.dto.MfaEnableResponse;
import pay.conflux.backend.identity.dto.MfaVerifyRequest;
import pay.conflux.backend.identity.usecase.DisableMfaUseCase;
import pay.conflux.backend.identity.usecase.EnableMfaUseCase;
import pay.conflux.backend.identity.usecase.VerifyMfaUseCase;

@RestController
@RequiredArgsConstructor
public class MfaControllerImpl implements MfaController {

  private final EnableMfaUseCase enableMfaUseCase;
  private final VerifyMfaUseCase verifyMfaUseCase;
  private final DisableMfaUseCase disableMfaUseCase;

  // TODO(wave-b): replace @RequestHeader("X-User-Id") with SecurityContextHolder principal.
  @Override
  public ResponseEntity<ApiResult<MfaEnableResponse>> enable(UUID userId) {
    return ApiResult.ok(enableMfaUseCase.execute(userId));
  }

  // TODO(wave-b): replace @RequestHeader("X-User-Id") with SecurityContextHolder principal.
  @Override
  public ResponseEntity<ApiResult<Void>> verify(UUID userId, MfaVerifyRequest request) {
    verifyMfaUseCase.execute(userId, request.code());
    return ApiResult.ok();
  }

  // TODO(wave-b): replace @RequestHeader("X-User-Id") with SecurityContextHolder principal.
  @Override
  public ResponseEntity<ApiResult<Void>> disable(UUID userId, MfaDisableRequest request) {
    disableMfaUseCase.execute(userId, request.password());
    return ApiResult.ok();
  }
}
