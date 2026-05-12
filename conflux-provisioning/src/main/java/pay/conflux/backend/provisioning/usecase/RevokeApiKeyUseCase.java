package pay.conflux.backend.provisioning.usecase;

import java.util.UUID;

/** Soft-revokes an API key and evicts its cache entry so the next lookup will 401. */
public interface RevokeApiKeyUseCase {

  void execute(UUID businessId, UUID apiKeyId);
}
