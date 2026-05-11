package pay.conflux.backend.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import pay.conflux.backend.risk.dto.RiskRuleDto;
import pay.conflux.backend.risk.entity.RiskRule;
import pay.conflux.backend.risk.mapper.RiskRuleMapper;
import pay.conflux.backend.risk.repository.RiskRuleRepository;

class ListRiskRulesUseCaseImplTest {

  @Test
  void shouldListRules() {
    RiskRuleRepository repo = mock(RiskRuleRepository.class);
    RiskRuleMapper mapper = new RiskRuleMapper();
    ListRiskRulesUseCaseImpl useCase = new ListRiskRulesUseCaseImpl(repo, mapper);

    PageRequest pageable = PageRequest.of(0, 10);
    when(repo.findByActiveTrueAndDeletedFalse(pageable))
        .thenReturn(new PageImpl<>(List.of(new RiskRule())));

    Page<RiskRuleDto> result = useCase.execute(pageable);
    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
  }
}
