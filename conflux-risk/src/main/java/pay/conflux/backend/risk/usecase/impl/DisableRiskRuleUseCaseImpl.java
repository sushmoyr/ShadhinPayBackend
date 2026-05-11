package pay.conflux.backend.risk.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.risk.entity.RiskRule;
import pay.conflux.backend.risk.events.RiskRuleChangedEvent;
import pay.conflux.backend.risk.repository.RiskRuleRepository;
import pay.conflux.backend.risk.usecase.internal.DisableRiskRuleUseCase;

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
