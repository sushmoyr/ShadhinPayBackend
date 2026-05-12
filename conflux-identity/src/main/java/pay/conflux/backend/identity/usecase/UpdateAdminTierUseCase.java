package pay.conflux.backend.identity.usecase;

import java.util.UUID;
import pay.conflux.backend.identity.dto.AdminProfileDto;
import pay.conflux.backend.identity.enums.AdminTier;

public interface UpdateAdminTierUseCase {
  AdminProfileDto execute(UUID targetUserId, AdminTier newTier);
}
