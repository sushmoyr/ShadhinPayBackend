package pay.conflux.backend.ledger.repository;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.ledger.entity.Posting;

@Repository
public interface PostingRepository extends JpaRepository<Posting, UUID> {
  Page<Posting> findByAccountId(UUID accountId, Pageable pageable);

  @Query(
      "SELECT SUM(CASE WHEN p.type = 'DEBIT' THEN p.amount ELSE -p.amount END) FROM Posting p WHERE"
          + " p.account.id = :accountId")
  BigDecimal sumAmountByAccountId(@Param("accountId") UUID accountId);
}
