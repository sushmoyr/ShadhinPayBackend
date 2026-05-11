package com.shadhinpay.risk.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import com.shadhinpay.risk.usecase.internal.DisableRiskRuleUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.expression.Expression;

@ExtendWith(MockitoExtension.class)
class CompiledRuleCacheTest {

  @Mock private RiskRuleRepository riskRuleRepository;
  @Mock private SafeSpelEvaluator safeSpelEvaluator;
  @Mock private DisableRiskRuleUseCase disableRiskRuleUseCase;
  @Mock private Expression expression;

  private CompiledRuleCache cache;

  @BeforeEach
  void setUp() {
    cache = new CompiledRuleCache(riskRuleRepository, safeSpelEvaluator, disableRiskRuleUseCase);
  }

  @Test
  void shouldLoadValidRulesAndDisableInvalid() {
    RiskRule valid = new RiskRule();
    valid.setId(UUID.randomUUID());
    valid.setExpression("valid");

    RiskRule invalid = new RiskRule();
    invalid.setId(UUID.randomUUID());
    invalid.setExpression("invalid");

    when(riskRuleRepository.findByActiveTrueAndDeletedFalse()).thenReturn(List.of(valid, invalid));
    when(safeSpelEvaluator.compile(valid)).thenReturn(expression);
    when(safeSpelEvaluator.compile(invalid)).thenReturn(null);

    cache.loadAll();

    assertThat(cache.snapshot()).hasSize(1);
    assertThat(cache.snapshot().iterator().next().rule()).isEqualTo(valid);
    verify(disableRiskRuleUseCase).execute(invalid.getId());
    verify(disableRiskRuleUseCase, never()).execute(valid.getId());
  }

  @Test
  void shouldPutAndInvalidate() {
    RiskRule rule = new RiskRule();
    rule.setId(UUID.randomUUID());

    cache.put(rule, expression);
    assertThat(cache.snapshot()).hasSize(1);

    cache.invalidate(rule.getId());
    assertThat(cache.snapshot()).isEmpty();
  }
}
