package pay.conflux.backend.provisioning.usecase;

import java.util.UUID;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.dto.CreateBusinessRequest;

/** Creates a new {@code Business} owned by the supplied merchant. */
public interface CreateBusinessUseCase {

  BusinessDto execute(UUID merchantId, CreateBusinessRequest request);
}
