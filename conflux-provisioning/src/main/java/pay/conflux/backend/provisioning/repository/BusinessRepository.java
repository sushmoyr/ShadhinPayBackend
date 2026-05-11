package pay.conflux.backend.provisioning.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.provisioning.entity.Business;

@Repository
public interface BusinessRepository
    extends JpaRepository<Business, UUID>, JpaSpecificationExecutor<Business> {

  List<Business> findByMerchantIdAndDeletedFalse(UUID merchantId);

  boolean existsByMerchantIdAndDeletedFalse(UUID merchantId);
}
