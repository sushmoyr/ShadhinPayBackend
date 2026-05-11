package com.shadhinpay.risk.usecase.internal;

import com.shadhinpay.risk.dto.RiskRuleDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListRiskRulesUseCase {
  Page<RiskRuleDto> execute(Pageable pageable);
}
