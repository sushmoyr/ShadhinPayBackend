package pay.conflux.backend.identity.usecase;

import java.util.UUID;
import pay.conflux.backend.identity.dto.BlockUserRequest;

public interface BlockUserUseCase {
  void execute(UUID userId, BlockUserRequest request);
}
