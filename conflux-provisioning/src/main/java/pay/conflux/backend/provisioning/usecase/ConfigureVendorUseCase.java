package pay.conflux.backend.provisioning.usecase;

import java.util.UUID;
import pay.conflux.backend.provisioning.dto.ConfigureVendorRequest;
import pay.conflux.backend.provisioning.dto.VendorConfigDto;

/** Upserts the {@code VendorConfig} for a {@code (business, vendor)} pair. */
public interface ConfigureVendorUseCase {

  VendorConfigDto execute(UUID businessId, ConfigureVendorRequest request);
}
