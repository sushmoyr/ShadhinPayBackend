package pay.conflux.backend.provisioning.usecase.impl;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.provisioning.dto.VendorConfigDto;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.VendorConfigRepository;
import pay.conflux.backend.provisioning.usecase.ListVendorConfigsUseCase;

@UseCase
@RequiredArgsConstructor
public class ListVendorConfigsUseCaseImpl implements ListVendorConfigsUseCase {

  private final VendorConfigRepository vendorConfigRepository;
  private final ProvisioningMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public List<VendorConfigDto> execute(UUID businessId) {
    return vendorConfigRepository.findAllByBusinessId(businessId).stream()
        .map(mapper::toDto)
        .toList();
  }
}
