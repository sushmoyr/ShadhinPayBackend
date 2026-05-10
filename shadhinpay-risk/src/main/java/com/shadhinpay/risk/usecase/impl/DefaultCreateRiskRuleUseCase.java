package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.error.DuplicateResourceException;
import com.shadhinpay.risk.dto.CreateRiskRuleRequest;
import com.shadhinpay.risk.dto.RiskRuleDto;
import com.shadhinpay.risk.engine.CompiledRuleCache;
import com.shadhinpay.risk.engine.SafeSpelEvaluator;
import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.mapper.RiskRuleMapper;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import com.shadhinpay.risk.usecase.internal.CreateRiskRuleUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultCreateRiskRuleUseCase implements CreateRiskRuleUseCase {

  private final RiskRuleRepository riskRuleRepository;
  private final RiskRuleMapper riskRuleMapper;
  private final CompiledRuleCache compiledRuleCache;
  private final SafeSpelEvaluator safeSpelEvaluator;

  public DefaultCreateRiskRuleUseCase(
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
  public RiskRuleDto execute(CreateRiskRuleRequest request) {
    if (riskRuleRepository.existsByNameAndDeletedFalse(request.name())) {
      throw new DuplicateResourceException("RiskRule", "name", request.name());
    }

    RiskRule rule = new RiskRule();
    rule.setName(request.name());
    rule.setExpression(request.expression());
    rule.setScoreWeight(request.scoreWeight());
    rule.setAction(request.action());

    RiskRule saved = riskRuleRepository.save(rule);

    org.springframework.expression.Expression expr = safeSpelEvaluator.compile(saved);
    if (expr != null) {
      compiledRuleCache.put(saved, expr);
    }

    return riskRuleMapper.toDto(saved);
  }
}
