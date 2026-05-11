package pay.conflux.backend.identity.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.identity.constant.IdentityRoutes;
import pay.conflux.backend.identity.dto.KycSubmissionRequest;
import pay.conflux.backend.identity.dto.MerchantOnboardingDto;

@Tag(name = "Merchant Onboarding", description = "KYC submission and onboarding status endpoints")
public interface MerchantOnboardingController {

  @PostMapping(IdentityRoutes.MERCHANT_KYC)
  ResponseEntity<ApiResult<MerchantOnboardingDto>> submitKyc(
      UUID userId, KycSubmissionRequest request);

  @GetMapping(IdentityRoutes.MERCHANT_ME)
  ResponseEntity<ApiResult<MerchantOnboardingDto>> getMe(UUID userId);
}
