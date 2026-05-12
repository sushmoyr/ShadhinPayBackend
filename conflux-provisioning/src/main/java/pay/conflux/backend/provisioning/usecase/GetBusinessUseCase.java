package pay.conflux.backend.provisioning.usecase;

import java.util.UUID;
import pay.conflux.backend.provisioning.dto.BusinessDto;

/**
 * Fetches a single business by id. Tenant isolation is the caller's responsibility — use {@code
 * BusinessOwnershipGuard} on merchant-facing controllers.
 */
public interface GetBusinessUseCase {

  BusinessDto execute(UUID businessId);
}
