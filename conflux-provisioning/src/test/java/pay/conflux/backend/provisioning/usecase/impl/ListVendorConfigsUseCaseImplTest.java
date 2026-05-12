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
import pay.conflux.backend.provisioning.constant.Vendor;
import pay.conflux.backend.provisioning.dto.VendorConfigDto;
import pay.conflux.backend.provisioning.entity.VendorConfig;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.VendorConfigRepository;

@ExtendWith(MockitoExtension.class)
class ListVendorConfigsUseCaseImplTest {

  @Mock private VendorConfigRepository vendorConfigRepository;
  @Mock private ProvisioningMapper mapper;
  @InjectMocks private ListVendorConfigsUseCaseImpl useCase;

  @Test
  void execute_returnsMappedConfigsForBusiness() {
    UUID businessId = UUID.randomUUID();
    VendorConfig vc = new VendorConfig();
    vc.setId(UUID.randomUUID());
    vc.setBusinessId(businessId);
    vc.setVendor(Vendor.BKASH);
    VendorConfigDto dto = new VendorConfigDto();
    dto.setId(vc.getId());
    when(vendorConfigRepository.findAllByBusinessId(businessId)).thenReturn(List.of(vc));
    when(mapper.toDto(vc)).thenReturn(dto);

    List<VendorConfigDto> result = useCase.execute(businessId);

    assertThat(result).containsExactly(dto);
  }

  @Test
  void execute_returnsEmptyListWhenNoVendorsConfigured() {
    UUID businessId = UUID.randomUUID();
    when(vendorConfigRepository.findAllByBusinessId(businessId)).thenReturn(List.of());

    assertThat(useCase.execute(businessId)).isEmpty();
  }
}
