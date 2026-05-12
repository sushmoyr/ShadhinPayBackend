package pay.conflux.backend.identity.controller.impl;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.identity.controller.MerchantOnboardingController;
import pay.conflux.backend.identity.dto.KycSubmissionRequest;
import pay.conflux.backend.identity.dto.MerchantOnboardingDto;
import pay.conflux.backend.identity.usecase.GetMerchantProfileUseCase;
import pay.conflux.backend.identity.usecase.SubmitKycDocumentsUseCase;

@RestController
@RequiredArgsConstructor
public class MerchantOnboardingControllerImpl implements MerchantOnboardingController {

  private final SubmitKycDocumentsUseCase submitKycDocumentsUseCase;
  private final GetMerchantProfileUseCase getMerchantProfileUseCase;

  // TODO(wave-b): replace @RequestHeader("X-User-Id") with SecurityContextHolder principal.
  @Override
  public ResponseEntity<ApiResult<MerchantOnboardingDto>> submitKyc(
      @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody KycSubmissionRequest request) {
    return ApiResult.ok(submitKycDocumentsUseCase.execute(userId, request));
  }

  // TODO(wave-b): replace @RequestHeader("X-User-Id") with SecurityContextHolder principal.
  @Override
  public ResponseEntity<ApiResult<MerchantOnboardingDto>> getMe(
      @RequestHeader("X-User-Id") UUID userId) {
    return ApiResult.ok(getMerchantProfileUseCase.execute(userId));
  }
}
