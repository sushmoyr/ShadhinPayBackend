package pay.conflux.backend.risk.usecase.internal;

import java.util.UUID;
import pay.conflux.backend.risk.dto.MerchantRiskProfileDto;

public interface GetMerchantRiskProfileUseCase {
  MerchantRiskProfileDto execute(UUID merchantId);
}
