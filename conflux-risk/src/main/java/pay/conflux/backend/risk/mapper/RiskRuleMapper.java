package pay.conflux.backend.risk.mapper;

import org.springframework.stereotype.Component;
import pay.conflux.backend.risk.dto.RiskRuleDto;
import pay.conflux.backend.risk.entity.RiskRule;

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
