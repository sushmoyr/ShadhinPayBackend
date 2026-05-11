package pay.conflux.backend.identity.usecase;

import java.util.UUID;
import pay.conflux.backend.identity.dto.MerchantOnboardingDto;

public interface GetMerchantProfileUseCase {
  MerchantOnboardingDto execute(UUID userId);
}
