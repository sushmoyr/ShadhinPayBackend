package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.BusinessRepository;

@ExtendWith(MockitoExtension.class)
class GetBusinessUseCaseImplTest {

  @Mock private BusinessRepository businessRepository;
  @Mock private ProvisioningMapper mapper;
  @InjectMocks private GetBusinessUseCaseImpl useCase;

  @Test
  void execute_returnsDto() {
    UUID id = UUID.randomUUID();
    Business b = new Business();
    b.setId(id);
    BusinessDto dto = new BusinessDto();
    dto.setId(id);
    when(businessRepository.findById(id)).thenReturn(Optional.of(b));
    when(mapper.toDto(b)).thenReturn(dto);

    assertThat(useCase.execute(id)).isSameAs(dto);
  }

  @Test
  void execute_missing_throwsResourceNotFound() {
    UUID id = UUID.randomUUID();
    when(businessRepository.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(id)).isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void execute_softDeleted_throwsResourceNotFound() {
    UUID id = UUID.randomUUID();
    Business b = new Business();
    b.setId(id);
    b.setDeleted(true);
    when(businessRepository.findById(id)).thenReturn(Optional.of(b));
    assertThatThrownBy(() -> useCase.execute(id)).isInstanceOf(ResourceNotFoundException.class);
  }
}
