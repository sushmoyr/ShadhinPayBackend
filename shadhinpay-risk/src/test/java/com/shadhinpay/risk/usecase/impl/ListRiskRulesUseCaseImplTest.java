package com.shadhinpay.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shadhinpay.risk.dto.RiskRuleDto;
import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.mapper.RiskRuleMapper;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
