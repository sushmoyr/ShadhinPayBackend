package pay.conflux.backend.provisioning.usecase;

import java.util.Map;
import java.util.UUID;

/**
 * Resolves the live (decrypted) credential map for a vendor configured on a business.
 *
 * <p><b>Caller:</b> {@code payment-core}'s adapter dispatch, invoked with the opaque {@code ref}
 * returned by {@link GetVendorConfigUseCase}. The decrypted map flows only from this resolver to
 * the adapter at dispatch time — adapters never see the encrypted form, and the plaintext map never
 * crosses an HTTP / module boundary.
 *
 * <p>Implementations must <b>never log the decrypted map</b>.
 */
public interface CredentialsResolver {

  /**
   * Returns the live credential map for {@code (businessId, vendor)}.
   *
   * <ul>
   *   <li>{@code PARTNER} mode: returns platform-supplied credentials from configuration.
   *   <li>{@code CUSTOM} mode: decrypts the per-business credentials stored on the vendor config.
   * </ul>
   *
   * @throws pay.conflux.backend.common.error.ResourceNotFoundException when no config exists
   * @throws pay.conflux.backend.common.error.ValidationException when {@code vendor} is unknown
   */
  Map<String, String> resolveCredentials(UUID businessId, String vendor);
}
