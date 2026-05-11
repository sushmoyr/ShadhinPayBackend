package pay.conflux.backend.risk.usecase.internal;

import pay.conflux.backend.risk.dto.CreateRiskRuleRequest;
import pay.conflux.backend.risk.dto.RiskRuleDto;

public interface CreateRiskRuleUseCase {
  RiskRuleDto execute(CreateRiskRuleRequest request);
}
