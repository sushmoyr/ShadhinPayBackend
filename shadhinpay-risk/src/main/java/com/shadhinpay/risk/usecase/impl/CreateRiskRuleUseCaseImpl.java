package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.error.DuplicateResourceException;
import com.shadhinpay.risk.dto.CreateRiskRuleRequest;
import com.shadhinpay.risk.dto.RiskRuleDto;
import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.events.RiskRuleChangedEvent;
import com.shadhinpay.risk.mapper.RiskRuleMapper;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import com.shadhinpay.risk.usecase.internal.CreateRiskRuleUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class CreateRiskRuleUseCaseImpl implements CreateRiskRuleUseCase {

  private final RiskRuleRepository riskRuleRepository;
  private final RiskRuleMapper riskRuleMapper;
  private final ApplicationEventPublisher eventPublisher;

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

    eventPublisher.publishEvent(
        new RiskRuleChangedEvent(saved.getId(), RiskRuleChangedEvent.ChangeKind.CREATED));

    return riskRuleMapper.toDto(saved);
  }
}
