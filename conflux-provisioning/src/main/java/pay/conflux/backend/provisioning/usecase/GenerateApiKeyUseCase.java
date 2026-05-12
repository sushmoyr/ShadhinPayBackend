package pay.conflux.backend.provisioning.usecase;

import java.util.UUID;
import pay.conflux.backend.provisioning.dto.ApiKeyDto;
import pay.conflux.backend.provisioning.dto.GenerateApiKeyRequest;

/**
 * Generates a fresh API key for the business. The returned {@link ApiKeyDto} carries the full
 * plaintext key <b>exactly once</b>; it is never persisted or returned by any other use case.
 */
public interface GenerateApiKeyUseCase {

  ApiKeyDto execute(UUID businessId, GenerateApiKeyRequest request);
}
