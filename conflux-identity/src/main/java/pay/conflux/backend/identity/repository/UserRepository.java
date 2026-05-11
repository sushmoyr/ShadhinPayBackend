package pay.conflux.backend.identity.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.IdentifierType;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

  Optional<User> findByIdentifierAndIdentifierTypeAndDeletedFalse(
      String identifier, IdentifierType identifierType);

  boolean existsByIdentifierAndIdentifierTypeAndDeletedFalse(
      String identifier, IdentifierType identifierType);
}
