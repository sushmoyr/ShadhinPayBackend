package com.shadhinpay.risk.usecase.internal;

import com.shadhinpay.risk.dto.CreateRiskRuleRequest;
import com.shadhinpay.risk.dto.RiskRuleDto;

public interface CreateRiskRuleUseCase {
  RiskRuleDto execute(CreateRiskRuleRequest request);
}
