package pay.conflux.backend.provisioning.usecase;

import java.util.UUID;

/**
 * Eviction port for the per-keyHash entry in the API-key lookup cache. Implemented by the
 * read-through use case so that key rotation and revocation can invalidate the cache without
 * reaching into the cache adapter directly.
 */
public interface ApiKeyCacheEvictor {

  void evictApiKeyByHash(UUID businessId, String keyHash);
}
