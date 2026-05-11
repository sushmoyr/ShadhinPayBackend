package pay.conflux.backend.provisioning.usecase;

import java.util.List;
import java.util.UUID;
import pay.conflux.backend.provisioning.dto.BusinessSummaryDto;

/** Lists every non-deleted business owned by a given merchant. */
public interface ListBusinessesUseCase {

  List<BusinessSummaryDto> execute(UUID merchantId);
}
