package pay.conflux.backend.provisioning.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.jpa.domain.Specification;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.entity.Business;

@Data
@AllArgsConstructor
public class BusinessSpec implements Specification<Business> {

  private final String search;
  private final UUID merchantId;
  private final BusinessStatus status;

  @Override
  public Predicate toPredicate(Root<Business> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
    List<Predicate> predicates = new ArrayList<>();
    predicates.add(cb.equal(root.get("deleted"), false));

    if (search != null && !search.isBlank()) {
      String pattern = "%" + search.toLowerCase() + "%";
      predicates.add(
          cb.or(
              cb.like(cb.lower(root.get("name")), pattern),
              cb.like(cb.lower(root.get("displayName")), pattern)));
    }
    if (merchantId != null) {
      predicates.add(cb.equal(root.get("merchantId"), merchantId));
    }
    if (status != null) {
      predicates.add(cb.equal(root.get("status"), status));
    }
    return cb.and(predicates.toArray(new Predicate[0]));
  }
}
