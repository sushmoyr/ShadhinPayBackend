package pay.conflux.backend.identity.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.identity.entity.MerchantProfile;

@Repository
public interface MerchantProfileRepository
    extends JpaRepository<MerchantProfile, UUID>, JpaSpecificationExecutor<MerchantProfile> {

  Optional<MerchantProfile> findByUserId(UUID userId);
}
