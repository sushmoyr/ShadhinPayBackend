package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.risk.engine.CompiledRuleCache;
import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import com.shadhinpay.risk.usecase.internal.DisableRiskRuleUseCase;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultDisableRiskRuleUseCase implements DisableRiskRuleUseCase {

  private final RiskRuleRepository riskRuleRepository;
  private final CompiledRuleCache compiledRuleCache;

  public DefaultDisableRiskRuleUseCase(
      RiskRuleRepository riskRuleRepository, @Lazy CompiledRuleCache compiledRuleCache) {
    this.riskRuleRepository = riskRuleRepository;
    this.compiledRuleCache = compiledRuleCache;
  }

  @Override
  @Transactional
  public void execute(UUID id) {
    RiskRule rule =
        riskRuleRepository
            .findById(id)
            .filter(r -> !r.isDeleted())
            .orElseThrow(() -> new ResourceNotFoundException("RiskRule", id));

    rule.setActive(false);
    rule.setDeleted(true); // Soft delete as per requirements
    riskRuleRepository.save(rule);
    compiledRuleCache.invalidate(rule.getId());
  }
}
