package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.provisioning.dto.BusinessSummaryDto;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.BusinessRepository;

@ExtendWith(MockitoExtension.class)
class ListBusinessesUseCaseImplTest {

  @Mock private BusinessRepository businessRepository;
  @Mock private ProvisioningMapper mapper;
  @InjectMocks private ListBusinessesUseCaseImpl useCase;

  @Test
  void execute_returnsMappedSummaries() {
    UUID merchantId = UUID.randomUUID();
    Business b = new Business();
    b.setId(UUID.randomUUID());
    BusinessSummaryDto dto = new BusinessSummaryDto();
    dto.setId(b.getId());
    when(businessRepository.findByMerchantIdAndDeletedFalse(merchantId)).thenReturn(List.of(b));
    when(mapper.toSummary(b)).thenReturn(dto);

    List<BusinessSummaryDto> result = useCase.execute(merchantId);

    assertThat(result).containsExactly(dto);
  }
}
