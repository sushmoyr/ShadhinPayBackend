package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import pay.conflux.backend.provisioning.dto.BusinessSummaryDto;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.spec.BusinessSpec;

@ExtendWith(MockitoExtension.class)
class SearchBusinessesUseCaseImplTest {

  @Mock private BusinessRepository businessRepository;
  @Mock private ProvisioningMapper mapper;
  @InjectMocks private SearchBusinessesUseCaseImpl useCase;

  @Test
  void execute_mapsResultPage() {
    Business b = new Business();
    b.setId(UUID.randomUUID());
    BusinessSummaryDto dto = new BusinessSummaryDto();
    dto.setId(b.getId());
    Page<Business> page = new PageImpl<>(List.of(b));
    when(businessRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);
    when(mapper.toSummary(b)).thenReturn(dto);

    Page<BusinessSummaryDto> result =
        useCase.execute(new BusinessSpec(null, null, null), Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(dto);
  }
}
