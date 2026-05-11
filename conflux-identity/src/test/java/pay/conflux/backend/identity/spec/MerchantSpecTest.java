package pay.conflux.backend.identity.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import pay.conflux.backend.identity.entity.MerchantProfile;
import pay.conflux.backend.identity.enums.OnboardingStatus;

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
  void hasOnboardingStatus_whenNull_returnsConjunction() {
    Specification<MerchantProfile> spec = MerchantSpec.hasOnboardingStatus(null);
    Root<MerchantProfile> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Predicate conjunction = mock(Predicate.class);
    when(cb.conjunction()).thenReturn(conjunction);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(conjunction);
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

  @Test
  void fullNameContains_whenNull_returnsConjunction() {
    Specification<MerchantProfile> spec = MerchantSpec.fullNameContains(null);
    Root<MerchantProfile> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Predicate conjunction = mock(Predicate.class);
    when(cb.conjunction()).thenReturn(conjunction);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(conjunction);
  }

  @Test
  void fullNameContains_whenBlank_returnsConjunction() {
    Specification<MerchantProfile> spec = MerchantSpec.fullNameContains("   ");
    Root<MerchantProfile> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Predicate conjunction = mock(Predicate.class);
    when(cb.conjunction()).thenReturn(conjunction);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(conjunction);
  }

  @Test
  void createdBetween_bothNull_returnsConjunction() {
    Specification<MerchantProfile> spec = MerchantSpec.createdBetween(null, null);
    Root<MerchantProfile> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Predicate conjunction = mock(Predicate.class);
    when(cb.conjunction()).thenReturn(conjunction);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(conjunction);
  }

  @Test
  void createdBetween_bothSet_usesBetween() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-02-01T00:00:00Z");
    Specification<MerchantProfile> spec = MerchantSpec.createdBetween(from, to);

    Root<MerchantProfile> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Path path = mock(Path.class);
    Predicate predicate = mock(Predicate.class);

    when(root.get("createdAt")).thenReturn(path);
    when(cb.between(path, from, to)).thenReturn(predicate);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(predicate);
  }

  @Test
  void createdBetween_onlyFrom_usesGreaterThanOrEqual() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Specification<MerchantProfile> spec = MerchantSpec.createdBetween(from, null);

    Root<MerchantProfile> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Path path = mock(Path.class);
    Predicate predicate = mock(Predicate.class);

    when(root.get("createdAt")).thenReturn(path);
    when(cb.greaterThanOrEqualTo(path, from)).thenReturn(predicate);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(predicate);
  }

  @Test
  void createdBetween_onlyTo_usesLessThanOrEqual() {
    Instant to = Instant.parse("2026-02-01T00:00:00Z");
    Specification<MerchantProfile> spec = MerchantSpec.createdBetween(null, to);

    Root<MerchantProfile> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Path path = mock(Path.class);
    Predicate predicate = mock(Predicate.class);

    when(root.get("createdAt")).thenReturn(path);
    when(cb.lessThanOrEqualTo(path, to)).thenReturn(predicate);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(predicate);
  }
}
