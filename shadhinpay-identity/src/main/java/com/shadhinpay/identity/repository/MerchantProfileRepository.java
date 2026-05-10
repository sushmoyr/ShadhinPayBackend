package com.shadhinpay.identity.repository;

import com.shadhinpay.identity.entity.MerchantProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantProfileRepository
    extends JpaRepository<MerchantProfile, UUID>, JpaSpecificationExecutor<MerchantProfile> {

  Optional<MerchantProfile> findByUserId(UUID userId);
}
