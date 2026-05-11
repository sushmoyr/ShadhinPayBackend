package pay.conflux.backend.identity.usecase;

import java.util.UUID;
import pay.conflux.backend.identity.dto.RejectMerchantRequest;

public interface RejectMerchantUseCase {
  void execute(UUID merchantProfileId, RejectMerchantRequest request);
}
