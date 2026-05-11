package pay.conflux.backend.provisioning.usecase;

import java.util.UUID;
import pay.conflux.backend.provisioning.dto.ApiKeyDto;

/**
 * Atomically revokes an existing API key and issues a new one with the same environment. The
 * plaintext for the new key is returned exactly once on the response.
 */
public interface RotateApiKeyUseCase {

  ApiKeyDto execute(UUID businessId, UUID apiKeyId);
}
