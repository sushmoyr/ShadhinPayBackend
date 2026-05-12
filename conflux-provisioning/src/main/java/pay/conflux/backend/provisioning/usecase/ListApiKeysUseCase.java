package pay.conflux.backend.provisioning.usecase;

import java.util.List;
import java.util.UUID;
import pay.conflux.backend.provisioning.dto.ApiKeySummaryDto;

/**
 * Lists the non-revoked API keys for a business. Returns only the safe-to-display {@link
 * ApiKeySummaryDto} shape — the plaintext key is only emitted at generate / rotate time and never
 * resurfaces from this endpoint.
 */
public interface ListApiKeysUseCase {

  List<ApiKeySummaryDto> execute(UUID businessId);
}
