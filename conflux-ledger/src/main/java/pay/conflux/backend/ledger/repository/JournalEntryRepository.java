package pay.conflux.backend.ledger.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.ledger.entity.JournalEntry;

@Repository
public interface JournalEntryRepository
    extends JpaRepository<JournalEntry, UUID>, JpaSpecificationExecutor<JournalEntry> {
  boolean existsBySourceTypeAndSourceId(String sourceType, String sourceId);

  @Override
  @EntityGraph(attributePaths = {"postings", "postings.account"})
  Page<JournalEntry> findAll(Specification<JournalEntry> spec, Pageable pageable);
}
