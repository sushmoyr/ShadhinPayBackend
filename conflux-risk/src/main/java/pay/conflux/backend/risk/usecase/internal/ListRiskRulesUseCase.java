package pay.conflux.backend.risk.usecase.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pay.conflux.backend.risk.dto.RiskRuleDto;

public interface ListRiskRulesUseCase {
  Page<RiskRuleDto> execute(Pageable pageable);
}
