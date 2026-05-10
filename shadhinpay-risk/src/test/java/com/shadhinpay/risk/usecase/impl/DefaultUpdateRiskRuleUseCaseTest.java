package com.shadhinpay.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.risk.dto.RiskRuleDto;
import com.shadhinpay.risk.dto.UpdateRiskRuleRequest;
import com.shadhinpay.risk.engine.CompiledRuleCache;
import com.shadhinpay.risk.engine.SafeSpelEvaluator;
import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.entity.RuleAction;
import com.shadhinpay.risk.mapper.RiskRuleMapper;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;

class DefaultUpdateRiskRuleUseCaseTest {

  private RiskRuleRepository riskRuleRepository;
  private RiskRuleMapper riskRuleMapper;
  private CompiledRuleCache compiledRuleCache;
  private SafeSpelEvaluator safeSpelEvaluator;
  private DefaultUpdateRiskRuleUseCase useCase;

  @BeforeEach
  void setUp() {
    riskRuleRepository = mock(RiskRuleRepository.class);
    riskRuleMapper = new RiskRuleMapper();
    compiledRuleCache = mock(CompiledRuleCache.class);
    safeSpelEvaluator = mock(SafeSpelEvaluator.class);
    useCase =
        new DefaultUpdateRiskRuleUseCase(
            riskRuleRepository, riskRuleMapper, compiledRuleCache, safeSpelEvaluator);
  }

  @Test
  void shouldUpdateRuleSuccessfully() {
    UUID id = UUID.randomUUID();
    UpdateRiskRuleRequest req = new UpdateRiskRuleRequest("expr2", 20, RuleAction.FLAG);
    RiskRule rule = new RiskRule();
    rule.setId(id);
    rule.setName("rule1");

    when(riskRuleRepository.findById(id)).thenReturn(Optional.of(rule));
    when(riskRuleRepository.save(any(RiskRule.class))).thenReturn(rule);
    when(safeSpelEvaluator.compile(any())).thenReturn(mock(Expression.class));

    RiskRuleDto res = useCase.execute(id, req);

    assertNotNull(res);
    assertEquals("expr2", rule.getExpression());
    verify(riskRuleRepository, times(1)).save(rule);
    verify(compiledRuleCache).put(any(), any());
  }

  @Test
  void shouldThrowNotFoundException() {
    UUID id = UUID.randomUUID();
    UpdateRiskRuleRequest req = new UpdateRiskRuleRequest("expr2", 20, RuleAction.FLAG);

    when(riskRuleRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> useCase.execute(id, req));
  }
}
