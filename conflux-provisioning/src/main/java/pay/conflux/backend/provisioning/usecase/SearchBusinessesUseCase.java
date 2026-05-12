package pay.conflux.backend.provisioning.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pay.conflux.backend.provisioning.dto.BusinessSummaryDto;
import pay.conflux.backend.provisioning.spec.BusinessSpec;

/** Admin-facing paginated search across all merchants' businesses. */
public interface SearchBusinessesUseCase {

  Page<BusinessSummaryDto> execute(BusinessSpec spec, Pageable pageable);
}
