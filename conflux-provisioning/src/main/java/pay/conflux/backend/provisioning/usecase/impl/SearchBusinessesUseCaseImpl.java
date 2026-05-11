package pay.conflux.backend.provisioning.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.provisioning.dto.BusinessSummaryDto;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.spec.BusinessSpec;
import pay.conflux.backend.provisioning.usecase.SearchBusinessesUseCase;

@UseCase
@RequiredArgsConstructor
public class SearchBusinessesUseCaseImpl implements SearchBusinessesUseCase {

  private final BusinessRepository businessRepository;
  private final ProvisioningMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public Page<BusinessSummaryDto> execute(BusinessSpec spec, Pageable pageable) {
    return businessRepository.findAll(spec, pageable).map(mapper::toSummary);
  }
}
