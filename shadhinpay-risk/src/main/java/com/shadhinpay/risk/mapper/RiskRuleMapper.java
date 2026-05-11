package com.shadhinpay.risk.mapper;

import com.shadhinpay.risk.dto.RiskRuleDto;
import com.shadhinpay.risk.entity.RiskRule;
import org.springframework.stereotype.Component;

@Component
public class RiskRuleMapper {

  public RiskRuleDto toDto(RiskRule entity) {
    if (entity == null) {
      return null;
    }
    return new RiskRuleDto(
        entity.getId(),
        entity.getName(),
        entity.getExpression(),
        entity.getScoreWeight(),
        entity.getAction(),
        entity.isActive());
  }
}
