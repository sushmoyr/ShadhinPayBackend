package pay.conflux.backend.identity.usecase;

import java.util.UUID;

public interface VerifyMerchantUseCase {
  void execute(UUID merchantProfileId);
}
