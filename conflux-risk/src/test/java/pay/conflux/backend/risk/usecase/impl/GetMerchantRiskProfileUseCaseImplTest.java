package pay.conflux.backend.risk.usecase.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.risk.dto.MerchantRiskProfileDto;
import pay.conflux.backend.risk.entity.MerchantRiskProfile;
import pay.conflux.backend.risk.mapper.MerchantRiskProfileMapper;
import pay.conflux.backend.risk.repository.MerchantRiskProfileRepository;

class GetMerchantRiskProfileUseCaseImplTest {

  @Test
  void shouldGetProfile() {
    MerchantRiskProfileRepository repo = mock(MerchantRiskProfileRepository.class);
    MerchantRiskProfileMapper mapper = new MerchantRiskProfileMapper();
    GetMerchantRiskProfileUseCaseImpl useCase = new GetMerchantRiskProfileUseCaseImpl(repo, mapper);

    UUID id = UUID.randomUUID();
    when(repo.findByMerchantId(id)).thenReturn(Optional.of(new MerchantRiskProfile()));

    MerchantRiskProfileDto res = useCase.execute(id);
    assertNotNull(res);
  }

  @Test
  void shouldThrowNotFound() {
    MerchantRiskProfileRepository repo = mock(MerchantRiskProfileRepository.class);
    MerchantRiskProfileMapper mapper = new MerchantRiskProfileMapper();
    GetMerchantRiskProfileUseCaseImpl useCase = new GetMerchantRiskProfileUseCaseImpl(repo, mapper);

    UUID id = UUID.randomUUID();
    when(repo.findByMerchantId(id)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> useCase.execute(id));
  }
}
