package com.shadhinpay.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.shadhinpay.common.error.DuplicateResourceException;
import com.shadhinpay.risk.dto.CreateRiskRuleRequest;
import com.shadhinpay.risk.dto.RiskRuleDto;
import com.shadhinpay.risk.engine.CompiledRuleCache;
import com.shadhinpay.risk.engine.SafeSpelEvaluator;
import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.entity.RuleAction;
import com.shadhinpay.risk.mapper.RiskRuleMapper;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;

class DefaultCreateRiskRuleUseCaseTest {

  private RiskRuleRepository riskRuleRepository;
  private RiskRuleMapper riskRuleMapper;
  private CompiledRuleCache compiledRuleCache;
  private SafeSpelEvaluator safeSpelEvaluator;
  private DefaultCreateRiskRuleUseCase useCase;

  @BeforeEach
  void setUp() {
    riskRuleRepository = mock(RiskRuleRepository.class);
    riskRuleMapper = new RiskRuleMapper();
    compiledRuleCache = mock(CompiledRuleCache.class);
    safeSpelEvaluator = mock(SafeSpelEvaluator.class);
    useCase =
        new DefaultCreateRiskRuleUseCase(
            riskRuleRepository, riskRuleMapper, compiledRuleCache, safeSpelEvaluator);
  }

  @Test
  void shouldCreateRuleSuccessfully() {
    CreateRiskRuleRequest req = new CreateRiskRuleRequest("rule1", "expr", 10, RuleAction.BLOCK);

    when(riskRuleRepository.existsByNameAndDeletedFalse("rule1")).thenReturn(false);
    when(riskRuleRepository.save(any(RiskRule.class)))
        .thenAnswer(
            i -> {
              RiskRule r = i.getArgument(0);
              r.setId(UUID.randomUUID());
              return r;
            });
    when(safeSpelEvaluator.compile(any())).thenReturn(mock(Expression.class));

    RiskRuleDto res = useCase.execute(req);

    assertNotNull(res);
    assertEquals("rule1", res.name());
    verify(riskRuleRepository, times(1)).save(any(RiskRule.class));
    verify(compiledRuleCache).put(any(), any());
  }

  @Test
  void shouldThrowDuplicateResourceExceptionIfNameExists() {
    CreateRiskRuleRequest req = new CreateRiskRuleRequest("rule1", "expr", 10, RuleAction.BLOCK);

    when(riskRuleRepository.existsByNameAndDeletedFalse("rule1")).thenReturn(true);

    assertThrows(DuplicateResourceException.class, () -> useCase.execute(req));
    verify(riskRuleRepository, never()).save(any());
  }
}
