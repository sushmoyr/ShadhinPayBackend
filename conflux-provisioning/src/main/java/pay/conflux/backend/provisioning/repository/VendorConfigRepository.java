package pay.conflux.backend.provisioning.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.provisioning.constant.Vendor;
import pay.conflux.backend.provisioning.entity.VendorConfig;

@Repository
public interface VendorConfigRepository extends JpaRepository<VendorConfig, UUID> {

  Optional<VendorConfig> findByBusinessIdAndVendor(UUID businessId, Vendor vendor);

  List<VendorConfig> findAllByBusinessId(UUID businessId);
}
