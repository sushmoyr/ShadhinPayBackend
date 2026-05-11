package pay.conflux.backend.risk.mapper;

import org.springframework.stereotype.Component;
import pay.conflux.backend.risk.dto.MerchantRiskProfileDto;
import pay.conflux.backend.risk.entity.MerchantRiskProfile;

@Component
public class MerchantRiskProfileMapper {

  public MerchantRiskProfileDto toDto(MerchantRiskProfile entity) {
    if (entity == null) {
      return null;
    }
    return new MerchantRiskProfileDto(
        entity.getMerchantId(), entity.getTrustLevel(), entity.getCustomLimits());
  }
}
