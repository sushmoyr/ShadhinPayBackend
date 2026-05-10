package com.shadhinpay.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shadhinpay.risk.engine.CompiledRuleCache;
import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultDisableRiskRuleUseCaseTest {

  @Test
  void shouldDisableRule() {
    RiskRuleRepository riskRuleRepository = mock(RiskRuleRepository.class);
    CompiledRuleCache compiledRuleCache = mock(CompiledRuleCache.class);
    DefaultDisableRiskRuleUseCase useCase =
        new DefaultDisableRiskRuleUseCase(riskRuleRepository, compiledRuleCache);

    UUID id = UUID.randomUUID();
    RiskRule rule = new RiskRule();
    rule.setId(id);
    rule.setActive(true);
    rule.setDeleted(false);

    when(riskRuleRepository.findById(id)).thenReturn(Optional.of(rule));

    useCase.execute(id);

    assertFalse(rule.isActive());
    assertTrue(rule.isDeleted());
    verify(riskRuleRepository, times(1)).save(rule);
    verify(compiledRuleCache).invalidate(id);
  }
}
