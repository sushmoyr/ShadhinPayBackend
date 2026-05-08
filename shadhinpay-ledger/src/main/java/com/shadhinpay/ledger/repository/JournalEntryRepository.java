package com.shadhinpay.ledger.repository;

import com.shadhinpay.ledger.entity.JournalEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
  boolean existsBySourceTypeAndSourceId(String sourceType, String sourceId);
}
