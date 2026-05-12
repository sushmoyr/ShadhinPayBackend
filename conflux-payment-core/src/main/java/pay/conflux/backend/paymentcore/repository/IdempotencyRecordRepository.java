package pay.conflux.backend.paymentcore.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.paymentcore.entity.IdempotencyRecord;
import pay.conflux.backend.paymentcore.entity.IdempotencyRecordId;

@Repository
public interface IdempotencyRecordRepository
    extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {

  @Query(
      "SELECT r FROM IdempotencyRecord r "
          + "WHERE r.id.businessId = :businessId AND r.id.requestKey = :requestKey")
  Optional<IdempotencyRecord> findByBusinessIdAndRequestKey(
      @Param("businessId") UUID businessId, @Param("requestKey") String requestKey);

  @Modifying
  @Query("DELETE FROM IdempotencyRecord r WHERE r.expiresAt < :threshold")
  int deleteAllByExpiresAtBefore(@Param("threshold") Instant threshold);
}
