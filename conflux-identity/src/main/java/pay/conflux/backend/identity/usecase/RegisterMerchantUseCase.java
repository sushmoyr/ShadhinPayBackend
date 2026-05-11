package pay.conflux.backend.identity.usecase;

import pay.conflux.backend.identity.dto.MerchantOnboardingDto;
import pay.conflux.backend.identity.dto.RegisterMerchantRequest;

public interface RegisterMerchantUseCase {
  MerchantOnboardingDto execute(RegisterMerchantRequest request);
}
