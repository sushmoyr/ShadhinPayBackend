package pay.conflux.backend.common.dto;

import org.springframework.data.domain.Page;

public record PaginationInfo(int page, int size, long totalElements, int totalPages) {

  public static PaginationInfo from(Page<?> page) {
    return new PaginationInfo(
        page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }
}
