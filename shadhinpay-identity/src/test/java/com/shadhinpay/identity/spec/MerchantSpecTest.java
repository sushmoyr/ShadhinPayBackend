package com.shadhinpay.identity.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shadhinpay.identity.entity.MerchantProfile;
import com.shadhinpay.identity.entity.enums.OnboardingStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class MerchantSpecTest {

  @Test
  void hasOnboardingStatus_returnsPredicate() {
    OnboardingStatus status = OnboardingStatus.VERIFIED;
    Specification<MerchantProfile> spec = MerchantSpec.hasOnboardingStatus(status);

    Root<MerchantProfile> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Path path = mock(Path.class);
    Predicate predicate = mock(Predicate.class);

    when(root.get("onboardingStatus")).thenReturn(path);
    when(cb.equal(path, status)).thenReturn(predicate);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(predicate);
  }

  @Test
  void fullNameContains_returnsPredicate() {
    String search = "Test";
    Specification<MerchantProfile> spec = MerchantSpec.fullNameContains(search);

    Root<MerchantProfile> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Path path = mock(Path.class);
    Predicate predicate = mock(Predicate.class);

    when(root.get("fullName")).thenReturn(path);
    when(cb.lower(path)).thenReturn(path);
    when(cb.like(path, "%" + search.toLowerCase() + "%")).thenReturn(predicate);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(predicate);
  }
}
