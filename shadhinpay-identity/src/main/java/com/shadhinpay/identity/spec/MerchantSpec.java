package com.shadhinpay.identity.spec;

import com.shadhinpay.identity.entity.MerchantProfile;
import com.shadhinpay.identity.entity.enums.OnboardingStatus;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

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
}
