package pay.conflux.backend.risk.usecase.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pay.conflux.backend.risk.dto.BlacklistEntryDto;

public interface ListBlacklistUseCase {
  Page<BlacklistEntryDto> execute(Pageable pageable);
}
