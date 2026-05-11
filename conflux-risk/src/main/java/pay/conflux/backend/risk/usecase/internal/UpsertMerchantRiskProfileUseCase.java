package pay.conflux.backend.risk.usecase.internal;

import java.util.UUID;
import pay.conflux.backend.risk.dto.MerchantRiskProfileDto;
import pay.conflux.backend.risk.dto.UpsertMerchantRiskProfileRequest;

public interface UpsertMerchantRiskProfileUseCase {
  MerchantRiskProfileDto execute(UUID merchantId, UpsertMerchantRiskProfileRequest request);
}
