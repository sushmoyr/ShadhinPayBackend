package pay.conflux.backend.identity.spec;

import java.time.Instant;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import pay.conflux.backend.identity.entity.MerchantProfile;
import pay.conflux.backend.identity.enums.OnboardingStatus;

public final class MerchantSpec {

  private MerchantSpec() {}

  public static Specification<MerchantProfile> hasOnboardingStatus(OnboardingStatus status) {
    return (root, query, cb) -> {
      if (status == null) {
        return cb.conjunction();
      }
      return cb.equal(root.get("onboardingStatus"), status);
    };
  }

  public static Specification<MerchantProfile> fullNameContains(String search) {
    return (root, query, cb) -> {
      if (search == null || search.isBlank()) {
        return cb.conjunction();
      }
      return cb.like(cb.lower(root.get("fullName")), "%" + search.toLowerCase(Locale.ROOT) + "%");
    };
  }

  public static Specification<MerchantProfile> createdBetween(Instant from, Instant to) {
    return (root, query, cb) -> {
      if (from == null && to == null) {
        return cb.conjunction();
      }
      if (from != null && to != null) {
        return cb.between(root.get("createdAt"), from, to);
      }
      if (from != null) {
        return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
      }
      return cb.lessThanOrEqualTo(root.get("createdAt"), to);
    };
  }
}
