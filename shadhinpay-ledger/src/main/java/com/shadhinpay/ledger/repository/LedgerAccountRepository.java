package com.shadhinpay.ledger.repository;

import com.shadhinpay.ledger.entity.LedgerAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
  Optional<LedgerAccount> findByOwnerIdAndCodeAndShardIdAndCurrency(
      UUID ownerId, String code, int shardId, String currency);

  List<LedgerAccount> findByCodeAndCurrency(String code, String currency);
}
