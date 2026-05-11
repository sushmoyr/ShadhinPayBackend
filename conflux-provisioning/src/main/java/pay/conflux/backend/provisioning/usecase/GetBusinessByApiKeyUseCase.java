package pay.conflux.backend.provisioning.usecase;

/**
 * Resolves the {@link BusinessContext} that owns a given API key.
 *
 * <p><b>Caller:</b> the global gateway / authentication filter on every authenticated inbound
 * request, and {@code payment-core} when establishing per-request tenant context.
 *
 * <p><b>Guarantees:</b>
 *
 * <ul>
 *   <li>Lookup is performed against the SHA-256 hash of the supplied key — the plaintext key is
 *       never logged or persisted.
 *   <li>Throws (rather than returning {@code null}) when the key is unknown, expired, or belongs to
 *       a blocked business; concrete exception type is defined by the implementation.
 *   <li>May be backed by a Redis cache; eviction is handled by the implementation when keys are
 *       rotated or businesses are blocked.
 * </ul>
 */
public interface GetBusinessByApiKeyUseCase {

  /**
   * Resolves the business that owns the supplied API key.
   *
   * @param apiKey the full plaintext API key from the {@code Authorization} header
   * @return the resolved {@link BusinessContext}; never {@code null}
   */
  BusinessContext execute(String apiKey);
}
