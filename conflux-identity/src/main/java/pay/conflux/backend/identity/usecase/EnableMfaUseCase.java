package pay.conflux.backend.identity.usecase;

import java.util.UUID;
import pay.conflux.backend.identity.dto.MfaEnableResponse;

public interface EnableMfaUseCase {
  MfaEnableResponse execute(UUID userId);
}
