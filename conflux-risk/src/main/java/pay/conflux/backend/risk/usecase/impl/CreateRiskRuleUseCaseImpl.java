package pay.conflux.backend.risk.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.DuplicateResourceException;
import pay.conflux.backend.risk.dto.CreateRiskRuleRequest;
import pay.conflux.backend.risk.dto.RiskRuleDto;
import pay.conflux.backend.risk.entity.RiskRule;
import pay.conflux.backend.risk.events.RiskRuleChangedEvent;
import pay.conflux.backend.risk.mapper.RiskRuleMapper;
import pay.conflux.backend.risk.repository.RiskRuleRepository;
import pay.conflux.backend.risk.usecase.internal.CreateRiskRuleUseCase;

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
