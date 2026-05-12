package pay.conflux.backend.identity.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.enums.AdminTier;

@Repository
public interface AdminProfileRepository extends JpaRepository<AdminProfile, UUID> {

  Optional<AdminProfile> findByUserId(UUID userId);

  Page<AdminProfile> findByAdminTier(AdminTier adminTier, Pageable pageable);

  /**
   * Counts SUPER admins whose underlying {@code User} is still {@code ACTIVE} and not soft-deleted.
   * Used by the last-SUPER guard in {@code UpdateAdminTierUseCase} and {@code DisableAdminUseCase}.
   */
  @Query(
      "SELECT COUNT(a) FROM AdminProfile a, User u "
          + "WHERE a.userId = u.id "
          + "AND a.adminTier = pay.conflux.backend.identity.enums.AdminTier.SUPER "
          + "AND u.status = pay.conflux.backend.identity.enums.UserStatus.ACTIVE "
          + "AND u.deleted = false")
  long countActiveSuperAdmins();
}
