package pay.conflux.backend.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import pay.conflux.backend.risk.dto.BlacklistEntryDto;
import pay.conflux.backend.risk.entity.BlacklistEntry;
import pay.conflux.backend.risk.mapper.BlacklistEntryMapper;
import pay.conflux.backend.risk.repository.BlacklistEntryRepository;

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
