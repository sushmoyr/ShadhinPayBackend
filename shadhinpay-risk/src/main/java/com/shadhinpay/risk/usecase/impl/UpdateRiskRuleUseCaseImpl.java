package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.risk.dto.RiskRuleDto;
import com.shadhinpay.risk.dto.UpdateRiskRuleRequest;
import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.events.RiskRuleChangedEvent;
import com.shadhinpay.risk.mapper.RiskRuleMapper;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import com.shadhinpay.risk.usecase.internal.UpdateRiskRuleUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class UpdateRiskRuleUseCaseImpl implements UpdateRiskRuleUseCase {

  private final RiskRuleRepository riskRuleRepository;
  private final RiskRuleMapper riskRuleMapper;
  private final ApplicationEventPublisher eventPublisher;

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

    eventPublisher.publishEvent(
        new RiskRuleChangedEvent(saved.getId(), RiskRuleChangedEvent.ChangeKind.UPDATED));

    return riskRuleMapper.toDto(saved);
  }
}
