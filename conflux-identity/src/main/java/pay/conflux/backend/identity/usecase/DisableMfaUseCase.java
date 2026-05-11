package pay.conflux.backend.identity.usecase;

import java.util.UUID;

public interface DisableMfaUseCase {
  void execute(UUID userId, String password);
}
