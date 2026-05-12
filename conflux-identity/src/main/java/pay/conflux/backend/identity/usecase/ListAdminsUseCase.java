package pay.conflux.backend.identity.usecase;

import org.springframework.data.domain.Page;
import pay.conflux.backend.common.dto.PaginationRequest;
import pay.conflux.backend.identity.dto.AdminProfileSummaryDto;
import pay.conflux.backend.identity.enums.AdminTier;

public interface ListAdminsUseCase {
  Page<AdminProfileSummaryDto> execute(PaginationRequest pagination, AdminTier tierFilter);
}
