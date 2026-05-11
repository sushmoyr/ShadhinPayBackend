package pay.conflux.backend.identity.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pay.conflux.backend.identity.dto.MerchantSummaryDto;
import pay.conflux.backend.identity.enums.OnboardingStatus;

public interface GetMerchantProfilesUseCase {
  Page<MerchantSummaryDto> execute(OnboardingStatus status, String search, Pageable pageable);
}
