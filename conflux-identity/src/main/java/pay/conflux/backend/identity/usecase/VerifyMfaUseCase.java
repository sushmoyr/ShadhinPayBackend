package pay.conflux.backend.identity.usecase;

import java.util.UUID;

public interface VerifyMfaUseCase {
  void execute(UUID userId, String code);
}
