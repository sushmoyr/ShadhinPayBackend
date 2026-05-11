package com.shadhinpay.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.shadhinpay.risk.dto.BlacklistEntryDto;
import com.shadhinpay.risk.entity.BlacklistEntry;
import com.shadhinpay.risk.mapper.BlacklistEntryMapper;
import com.shadhinpay.risk.repository.BlacklistEntryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class ListBlacklistUseCaseImplTest {

  @Test
  void shouldListEntries() {
    BlacklistEntryRepository repo = mock(BlacklistEntryRepository.class);
    BlacklistEntryMapper mapper = new BlacklistEntryMapper();
    ListBlacklistUseCaseImpl useCase = new ListBlacklistUseCaseImpl(repo, mapper);

    when(repo.findAllActive(any())).thenReturn(List.of(new BlacklistEntry()));

    Page<BlacklistEntryDto> res = useCase.execute(PageRequest.of(0, 10));

    assertNotNull(res);
    assertEquals(1, res.getTotalElements());
  }
}
