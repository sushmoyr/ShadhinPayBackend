package pay.conflux.backend.provisioning.usecase.impl;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.provisioning.dto.BusinessSummaryDto;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.ListBusinessesUseCase;

@UseCase
@RequiredArgsConstructor
public class ListBusinessesUseCaseImpl implements ListBusinessesUseCase {

  private final BusinessRepository businessRepository;
  private final ProvisioningMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public List<BusinessSummaryDto> execute(UUID merchantId) {
    return businessRepository.findByMerchantIdAndDeletedFalse(merchantId).stream()
        .map(mapper::toSummary)
        .toList();
  }
}
