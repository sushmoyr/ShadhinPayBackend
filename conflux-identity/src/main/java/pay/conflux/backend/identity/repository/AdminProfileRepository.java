package pay.conflux.backend.identity.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.identity.entity.AdminProfile;

@Repository
public interface AdminProfileRepository extends JpaRepository<AdminProfile, UUID> {

  Optional<AdminProfile> findByUserId(UUID userId);
}
