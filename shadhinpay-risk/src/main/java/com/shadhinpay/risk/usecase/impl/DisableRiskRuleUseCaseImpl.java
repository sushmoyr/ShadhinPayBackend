package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.risk.entity.RiskRule;
import com.shadhinpay.risk.events.RiskRuleChangedEvent;
import com.shadhinpay.risk.repository.RiskRuleRepository;
import com.shadhinpay.risk.usecase.internal.DisableRiskRuleUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class DisableRiskRuleUseCaseImpl implements DisableRiskRuleUseCase {

  private final RiskRuleRepository riskRuleRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public void execute(UUID id) {
    RiskRule rule =
        riskRuleRepository
            .findById(id)
            .filter(r -> !r.isDeleted())
            .orElseThrow(() -> new ResourceNotFoundException("RiskRule", id));

    rule.setActive(false);
    rule.setDeleted(true);
    riskRuleRepository.save(rule);

    eventPublisher.publishEvent(
        new RiskRuleChangedEvent(rule.getId(), RiskRuleChangedEvent.ChangeKind.DISABLED));
  }
}
