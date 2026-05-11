package pay.conflux.backend.risk.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.risk.dto.RiskRuleDto;
import pay.conflux.backend.risk.dto.UpdateRiskRuleRequest;
import pay.conflux.backend.risk.entity.RiskRule;
import pay.conflux.backend.risk.events.RiskRuleChangedEvent;
import pay.conflux.backend.risk.mapper.RiskRuleMapper;
import pay.conflux.backend.risk.repository.RiskRuleRepository;
import pay.conflux.backend.risk.usecase.internal.UpdateRiskRuleUseCase;

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
