package pay.conflux.backend.risk.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.risk.dto.RiskRuleDto;
import pay.conflux.backend.risk.mapper.RiskRuleMapper;
import pay.conflux.backend.risk.repository.RiskRuleRepository;
import pay.conflux.backend.risk.usecase.internal.ListRiskRulesUseCase;

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
