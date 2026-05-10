package com.shadhinpay.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.risk.dto.MerchantRiskProfileDto;
import com.shadhinpay.risk.entity.MerchantRiskProfile;
import com.shadhinpay.risk.mapper.MerchantRiskProfileMapper;
import com.shadhinpay.risk.repository.MerchantRiskProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultGetMerchantRiskProfileUseCaseTest {

  @Test
  void shouldGetProfile() {
    MerchantRiskProfileRepository repo = mock(MerchantRiskProfileRepository.class);
    MerchantRiskProfileMapper mapper = new MerchantRiskProfileMapper();
    DefaultGetMerchantRiskProfileUseCase useCase =
        new DefaultGetMerchantRiskProfileUseCase(repo, mapper);

    UUID id = UUID.randomUUID();
    when(repo.findByMerchantId(id)).thenReturn(Optional.of(new MerchantRiskProfile()));

    MerchantRiskProfileDto res = useCase.execute(id);
    assertNotNull(res);
  }

  @Test
  void shouldThrowNotFound() {
    MerchantRiskProfileRepository repo = mock(MerchantRiskProfileRepository.class);
    MerchantRiskProfileMapper mapper = new MerchantRiskProfileMapper();
    DefaultGetMerchantRiskProfileUseCase useCase =
        new DefaultGetMerchantRiskProfileUseCase(repo, mapper);

    UUID id = UUID.randomUUID();
    when(repo.findByMerchantId(id)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> useCase.execute(id));
  }
}
