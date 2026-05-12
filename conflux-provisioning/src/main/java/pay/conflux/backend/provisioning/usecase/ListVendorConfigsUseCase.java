package pay.conflux.backend.provisioning.usecase;

import java.util.List;
import java.util.UUID;
import pay.conflux.backend.provisioning.dto.VendorConfigDto;

/**
 * Lists every vendor configuration persisted for a business. Used by the merchant dashboard to
 * hydrate the vendor-config tab on page load — credentials are never returned, only the {@code
 * (vendor, mode, configured)} metadata exposed by {@link VendorConfigDto}.
 */
public interface ListVendorConfigsUseCase {

  List<VendorConfigDto> execute(UUID businessId);
}
