package com.shadhinpay.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PaginationInfoTest {

  @Test
  void from_extractsAllPageMetadata() {
    Page<String> page = new PageImpl<>(List.of("a", "b", "c"), PageRequest.of(1, 3), 10L);

    PaginationInfo info = PaginationInfo.from(page);

    assertThat(info.page()).isEqualTo(1);
    assertThat(info.size()).isEqualTo(3);
    assertThat(info.totalElements()).isEqualTo(10L);
    assertThat(info.totalPages()).isEqualTo(4);
  }
}
