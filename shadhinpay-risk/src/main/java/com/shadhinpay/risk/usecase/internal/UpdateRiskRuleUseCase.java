package com.shadhinpay.risk.usecase.internal;

import com.shadhinpay.risk.dto.RiskRuleDto;
import com.shadhinpay.risk.dto.UpdateRiskRuleRequest;
import java.util.UUID;

public interface UpdateRiskRuleUseCase {
  RiskRuleDto execute(UUID id, UpdateRiskRuleRequest request);
}
