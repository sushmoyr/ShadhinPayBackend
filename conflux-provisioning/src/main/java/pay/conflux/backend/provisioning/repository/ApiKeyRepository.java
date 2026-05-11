package pay.conflux.backend.provisioning.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.provisioning.constant.Environment;
import pay.conflux.backend.provisioning.entity.ApiKey;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

  Optional<ApiKey> findByKeyHashAndRevokedFalse(String keyHash);

  List<ApiKey> findAllByBusinessIdAndRevokedFalse(UUID businessId);

  List<ApiKey> findAllByBusinessIdAndEnvironment(UUID businessId, Environment environment);

  @Modifying
  @Query("update ApiKey a set a.lastUsedAt = :usedAt where a.id = :id")
  @Lock(LockModeType.NONE)
  int markLastUsedAt(@Param("id") UUID id, @Param("usedAt") Instant usedAt);
}
