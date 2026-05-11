package pay.conflux.backend.risk.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.risk.dto.MerchantRiskProfileDto;
import pay.conflux.backend.risk.entity.MerchantRiskProfile;
import pay.conflux.backend.risk.entity.TrustLevel;

class MerchantRiskProfileMapperTest {

  private final MerchantRiskProfileMapper mapper = new MerchantRiskProfileMapper();

  @Test
  void shouldMapToDto() {
    MerchantRiskProfile profile = new MerchantRiskProfile();
    profile.setMerchantId(UUID.randomUUID());
    profile.setTrustLevel(TrustLevel.VIP);
    profile.setCustomLimits("limits");

    MerchantRiskProfileDto dto = mapper.toDto(profile);

    assertNotNull(dto);
    assertEquals(profile.getMerchantId(), dto.merchantId());
    assertEquals(profile.getTrustLevel(), dto.trustLevel());
    assertEquals(profile.getCustomLimits(), dto.customLimits());
  }

  @Test
  void shouldReturnNullWhenNull() {
    assertNull(mapper.toDto(null));
  }
}
