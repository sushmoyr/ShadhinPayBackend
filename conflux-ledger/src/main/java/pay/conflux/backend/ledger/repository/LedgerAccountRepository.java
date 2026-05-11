package pay.conflux.backend.ledger.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.ledger.entity.LedgerAccount;

@Repository
public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
  Optional<LedgerAccount> findByOwnerIdAndCodeAndShardIdAndCurrency(
      UUID ownerId, String code, int shardId, String currency);

  List<LedgerAccount> findByCodeAndCurrency(String code, String currency);
}
