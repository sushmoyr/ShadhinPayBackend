package pay.conflux.backend.provisioning.usecase;

import java.util.UUID;

/**
 * Provisioning-owned read-through port to the identity module's {@code users.status} column.
 *
 * <p>Used by use cases that need to gate behaviour on whether the owning merchant is currently
 * {@code ACTIVE} (e.g. blocking LIVE API-key generation when the merchant is blocked). Implemented
 * by {@code IdentityBackedMerchantStatusPort} with a raw JDBC read so that the provisioning module
 * does not take a compile-time dependency on the identity module's entity surface.
 */
public interface MerchantStatusPort {

  /**
   * @return {@code true} if a user row exists for {@code merchantId} and its status equals {@code
   *     "ACTIVE"}.
   */
  boolean isActive(UUID merchantId);
}
