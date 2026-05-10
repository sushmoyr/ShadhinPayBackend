package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.risk.dto.RiskRuleDto;
import com.shadhinpay.risk.mapper.RiskRuleMapper;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import com.shadhinpay.risk.usecase.internal.ListRiskRulesUseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultListRiskRulesUseCase implements ListRiskRulesUseCase {

  private final RiskRuleRepository riskRuleRepository;
  private final RiskRuleMapper riskRuleMapper;

  public DefaultListRiskRulesUseCase(
      RiskRuleRepository riskRuleRepository, RiskRuleMapper riskRuleMapper) {
    this.riskRuleRepository = riskRuleRepository;
    this.riskRuleMapper = riskRuleMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<RiskRuleDto> execute(Pageable pageable) {
    return riskRuleRepository.findByActiveTrueAndDeletedFalse(pageable).map(riskRuleMapper::toDto);
  }
}
