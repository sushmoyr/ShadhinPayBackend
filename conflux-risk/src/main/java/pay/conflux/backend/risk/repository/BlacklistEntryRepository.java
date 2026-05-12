package pay.conflux.backend.risk.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.risk.entity.BlacklistEntry;
import pay.conflux.backend.risk.enums.BlacklistType;

@Repository
public interface BlacklistEntryRepository extends JpaRepository<BlacklistEntry, UUID> {

  @Query(
      "SELECT b FROM BlacklistEntry b "
          + "WHERE b.type = :type AND b.value = :val "
          + "AND b.deleted = false "
          + "AND (b.expiresAt IS NULL OR b.expiresAt > :now)")
  Optional<BlacklistEntry> findActiveByTypeAndValue(
      @Param("type") BlacklistType type, @Param("val") String value, @Param("now") Instant now);

  @Query(
      "SELECT b FROM BlacklistEntry b "
          + "WHERE b.type = :type "
          + "AND b.deleted = false "
          + "AND (b.expiresAt IS NULL OR b.expiresAt > :now)")
  List<BlacklistEntry> findAllActiveByType(
      @Param("type") BlacklistType type, @Param("now") Instant now);

  @Query(
      "SELECT b FROM BlacklistEntry b "
          + "WHERE b.deleted = false "
          + "AND (b.expiresAt IS NULL OR b.expiresAt > :now)")
  List<BlacklistEntry> findAllActive(@Param("now") Instant now);
}
