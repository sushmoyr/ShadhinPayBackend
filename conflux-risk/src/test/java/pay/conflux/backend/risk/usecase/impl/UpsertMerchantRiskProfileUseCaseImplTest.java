package pay.conflux.backend.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.risk.dto.MerchantRiskProfileDto;
import pay.conflux.backend.risk.dto.UpsertMerchantRiskProfileRequest;
import pay.conflux.backend.risk.entity.MerchantRiskProfile;
import pay.conflux.backend.risk.enums.TrustLevel;
import pay.conflux.backend.risk.mapper.MerchantRiskProfileMapper;
import pay.conflux.backend.risk.repository.MerchantRiskProfileRepository;

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
