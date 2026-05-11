package pay.conflux.backend.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PaginationRequestTest {

  @Test
  void toPageable_returnsUnpagedWhenPaginateFalse() {
    PaginationRequest request =
        new PaginationRequest(0, 20, "createdAt", Sort.Direction.DESC, false);

    Pageable pageable = request.toPageable();

    assertThat(pageable.isUnpaged()).isTrue();
  }

  @Test
  void toPageable_buildsPageRequestWithSortWhenPaginateTrue() {
    PaginationRequest request = new PaginationRequest(2, 10, "name", Sort.Direction.ASC, true);

    Pageable pageable = request.toPageable();

    assertThat(pageable.isPaged()).isTrue();
    assertThat(pageable.getPageNumber()).isEqualTo(2);
    assertThat(pageable.getPageSize()).isEqualTo(10);
    assertThat(pageable.getSort().getOrderFor("name"))
        .isNotNull()
        .extracting(Sort.Order::getDirection)
        .isEqualTo(Sort.Direction.ASC);
  }

  @Test
  void defaults_areSensible() {
    PaginationRequest request = new PaginationRequest();

    assertThat(request.getPage()).isZero();
    assertThat(request.getSize()).isEqualTo(20);
    assertThat(request.getSortBy()).isEqualTo("createdAt");
    assertThat(request.getOrder()).isEqualTo(Sort.Direction.DESC);
    assertThat(request.isPaginate()).isTrue();
  }
}
