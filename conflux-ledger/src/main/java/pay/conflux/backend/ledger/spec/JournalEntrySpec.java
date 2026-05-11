package pay.conflux.backend.ledger.spec;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import pay.conflux.backend.ledger.entity.JournalEntry;
import pay.conflux.backend.ledger.entity.Posting;

public final class JournalEntrySpec {

  private JournalEntrySpec() {}

  public static Specification<JournalEntry> filterBy(
      String sourceType, Instant startDate, Instant endDate, UUID ownerId) {

    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (sourceType != null && !sourceType.isBlank()) {
        predicates.add(cb.equal(root.get("sourceType"), sourceType));
      }

      if (startDate != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), startDate));
      }

      if (endDate != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), endDate));
      }

      if (ownerId != null) {
        Join<JournalEntry, Posting> postingJoin = root.join("postings", JoinType.INNER);
        predicates.add(cb.equal(postingJoin.get("account").get("ownerId"), ownerId));
        if (query != null) {
          query.distinct(true);
        }
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
