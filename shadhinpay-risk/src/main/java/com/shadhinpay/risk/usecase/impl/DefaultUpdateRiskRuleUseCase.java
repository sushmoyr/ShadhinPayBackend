package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.risk.dto.RiskRuleDto;
import com.shadhinpay.risk.dto.UpdateRiskRuleRequest;
import com.shadhinpay.risk.engine.CompiledRuleCache;
import com.shadhinpay.risk.engine.SafeSpelEvaluator;
import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.mapper.RiskRuleMapper;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import com.shadhinpay.risk.usecase.internal.UpdateRiskRuleUseCase;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultUpdateRiskRuleUseCase implements UpdateRiskRuleUseCase {

  private final RiskRuleRepository riskRuleRepository;
  private final RiskRuleMapper riskRuleMapper;
  private final CompiledRuleCache compiledRuleCache;
  private final SafeSpelEvaluator safeSpelEvaluator;

  public DefaultUpdateRiskRuleUseCase(
      RiskRuleRepository riskRuleRepository,
      RiskRuleMapper riskRuleMapper,
      CompiledRuleCache compiledRuleCache,
      SafeSpelEvaluator safeSpelEvaluator) {
    this.riskRuleRepository = riskRuleRepository;
    this.riskRuleMapper = riskRuleMapper;
    this.compiledRuleCache = compiledRuleCache;
    this.safeSpelEvaluator = safeSpelEvaluator;
  }

  @Override
  @Transactional
  public RiskRuleDto execute(UUID id, UpdateRiskRuleRequest request) {
    RiskRule rule =
        riskRuleRepository
            .findById(id)
            .filter(r -> !r.isDeleted())
            .orElseThrow(() -> new ResourceNotFoundException("RiskRule", id));

    rule.setExpression(request.expression());
    rule.setScoreWeight(request.scoreWeight());
    rule.setAction(request.action());

    RiskRule saved = riskRuleRepository.save(rule);

    org.springframework.expression.Expression expr = safeSpelEvaluator.compile(saved);
    if (expr != null) {
      compiledRuleCache.put(saved, expr);
    } else {
      compiledRuleCache.invalidate(saved.getId());
    }

    return riskRuleMapper.toDto(saved);
  }
}
