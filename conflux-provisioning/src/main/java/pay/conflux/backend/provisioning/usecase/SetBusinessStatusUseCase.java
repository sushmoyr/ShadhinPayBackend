package pay.conflux.backend.provisioning.usecase;

import java.util.UUID;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.dto.BusinessDto;

/**
 * Admin-only state transition for a business. Transitioning to {@code INACTIVE} also evicts every
 * cached API key for the business so that subsequent requests bypass the warm cache and re-resolve
 * against the DB (where they will be rejected).
 */
public interface SetBusinessStatusUseCase {

  BusinessDto execute(UUID businessId, BusinessStatus status);
}
