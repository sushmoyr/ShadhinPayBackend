package pay.conflux.backend.risk.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.risk.entity.MerchantRiskProfile;

@Repository
public interface MerchantRiskProfileRepository extends JpaRepository<MerchantRiskProfile, UUID> {
  Optional<MerchantRiskProfile> findByMerchantId(UUID merchantId);
}
