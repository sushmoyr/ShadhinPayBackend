package pay.conflux.backend.risk.usecase.internal;

import java.util.UUID;
import pay.conflux.backend.risk.dto.RiskRuleDto;
import pay.conflux.backend.risk.dto.UpdateRiskRuleRequest;

public interface UpdateRiskRuleUseCase {
  RiskRuleDto execute(UUID id, UpdateRiskRuleRequest request);
}
