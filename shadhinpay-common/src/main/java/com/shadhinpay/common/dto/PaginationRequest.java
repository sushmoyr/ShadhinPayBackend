package com.shadhinpay.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaginationRequest {

  private int page = 0;
  private int size = 20;
  private String sortBy = "createdAt";
  private Sort.Direction order = Sort.Direction.DESC;
  private boolean paginate = true;

  public Pageable toPageable() {
    if (!paginate) {
      return Pageable.unpaged();
    }
    return PageRequest.of(page, size, Sort.by(order, sortBy));
  }
}
