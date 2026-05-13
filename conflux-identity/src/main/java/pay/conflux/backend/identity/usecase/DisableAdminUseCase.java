package pay.conflux.backend.identity.usecase;

import java.util.UUID;

public interface DisableAdminUseCase {
  void execute(UUID targetUserId, UUID callerUserId);
}
