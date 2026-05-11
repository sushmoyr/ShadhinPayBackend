package pay.conflux.backend.provisioning.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.GetBusinessUseCase;

@UseCase
@RequiredArgsConstructor
public class GetBusinessUseCaseImpl implements GetBusinessUseCase {

  private final BusinessRepository businessRepository;
  private final ProvisioningMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public BusinessDto execute(UUID businessId) {
    Business business =
        businessRepository
            .findById(businessId)
            .filter(b -> !b.isDeleted())
            .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    return mapper.toDto(business);
  }
}
