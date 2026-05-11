package pay.conflux.backend.identity.usecase;

import java.util.UUID;
import pay.conflux.backend.identity.dto.KycSubmissionRequest;
import pay.conflux.backend.identity.dto.MerchantOnboardingDto;

public interface SubmitKycDocumentsUseCase {
  MerchantOnboardingDto execute(UUID userId, KycSubmissionRequest request);
}
