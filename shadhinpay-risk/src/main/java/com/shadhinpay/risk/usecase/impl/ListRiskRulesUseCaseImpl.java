package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.risk.dto.RiskRuleDto;
import com.shadhinpay.risk.mapper.RiskRuleMapper;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import com.shadhinpay.risk.usecase.internal.ListRiskRulesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class ListRiskRulesUseCaseImpl implements ListRiskRulesUseCase {

  private final RiskRuleRepository riskRuleRepository;
  private final RiskRuleMapper riskRuleMapper;

  @Override
  @Transactional(readOnly = true)
  public Page<RiskRuleDto> execute(Pageable pageable) {
    return riskRuleRepository.findByActiveTrueAndDeletedFalse(pageable).map(riskRuleMapper::toDto);
  }
}
