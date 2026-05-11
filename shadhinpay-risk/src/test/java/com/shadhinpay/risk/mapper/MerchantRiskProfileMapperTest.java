package com.shadhinpay.risk.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.shadhinpay.risk.dto.MerchantRiskProfileDto;
import com.shadhinpay.risk.entity.MerchantRiskProfile;
import com.shadhinpay.risk.entity.TrustLevel;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
