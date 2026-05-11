package com.shadhinpay.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shadhinpay.risk.dto.MerchantRiskProfileDto;
import com.shadhinpay.risk.dto.UpsertMerchantRiskProfileRequest;
import com.shadhinpay.risk.entity.MerchantRiskProfile;
import com.shadhinpay.risk.entity.TrustLevel;
import com.shadhinpay.risk.mapper.MerchantRiskProfileMapper;
import com.shadhinpay.risk.repository.MerchantRiskProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpsertMerchantRiskProfileUseCaseImplTest {

  @Test
  void shouldUpsertProfile() {
    MerchantRiskProfileRepository repo = mock(MerchantRiskProfileRepository.class);
    MerchantRiskProfileMapper mapper = new MerchantRiskProfileMapper();
    UpsertMerchantRiskProfileUseCaseImpl useCase =
        new UpsertMerchantRiskProfileUseCaseImpl(repo, mapper);

    UUID id = UUID.randomUUID();
    UpsertMerchantRiskProfileRequest req =
        new UpsertMerchantRiskProfileRequest(TrustLevel.VIP, "limits");

    when(repo.findByMerchantId(id)).thenReturn(Optional.empty());
    when(repo.save(any(MerchantRiskProfile.class))).thenAnswer(i -> i.getArgument(0));

    MerchantRiskProfileDto res = useCase.execute(id, req);

    assertNotNull(res);
    assertEquals(TrustLevel.VIP, res.trustLevel());
    verify(repo, times(1)).save(any());
  }
}
