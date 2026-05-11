package pay.conflux.backend.risk.usecase.internal;

import pay.conflux.backend.risk.dto.AddBlacklistEntryRequest;
import pay.conflux.backend.risk.dto.BlacklistEntryDto;

public interface AddBlacklistEntryUseCase {
  BlacklistEntryDto execute(AddBlacklistEntryRequest request);
}
