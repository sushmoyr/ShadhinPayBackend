package com.shadhinpay.quota.repository;

import com.shadhinpay.quota.entity.QuotaUsage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QuotaUsageRepository extends JpaRepository<QuotaUsage, UUID> {
  Optional<QuotaUsage> findByMerchantIdAndPeriod(UUID merchantId, String period);

  Page<QuotaUsage> findByMerchantIdAndPeriod(UUID merchantId, String period, Pageable pageable);

  @Modifying
  @Query(
      nativeQuery = true,
      value =
          "INSERT INTO quota_usage (id, merchant_id, period, partner_mode_count, created_at,"
              + " updated_at, version) VALUES (gen_random_uuid(), :merchantId, :period, :count,"
              + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0) ON CONFLICT (merchant_id, period) DO"
              + " UPDATE SET partner_mode_count = EXCLUDED.partner_mode_count, updated_at ="
              + " CURRENT_TIMESTAMP, version = quota_usage.version + 1")
  void upsertUsage(
      @Param("merchantId") UUID merchantId,
      @Param("period") String period,
      @Param("count") int count);
}
