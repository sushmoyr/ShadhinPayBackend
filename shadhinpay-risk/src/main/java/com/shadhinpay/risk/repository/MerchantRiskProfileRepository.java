package com.shadhinpay.risk.repository;

import com.shadhinpay.risk.entity.MerchantRiskProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantRiskProfileRepository extends JpaRepository<MerchantRiskProfile, UUID> {
  Optional<MerchantRiskProfile> findByMerchantId(UUID merchantId);
}
