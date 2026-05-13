package pay.conflux.backend.identity.usecase;

import java.util.UUID;
import pay.conflux.backend.identity.dto.AdminProfileDto;

public interface GetAdminProfileUseCase {

  AdminProfileDto execute(UUID userId);
}
