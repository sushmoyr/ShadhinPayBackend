package pay.conflux.backend.provisioning.usecase.impl;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.provisioning.dto.ApiKeySummaryDto;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;
import pay.conflux.backend.provisioning.usecase.ListApiKeysUseCase;

@UseCase
@RequiredArgsConstructor
public class ListApiKeysUseCaseImpl implements ListApiKeysUseCase {

  private final ApiKeyRepository apiKeyRepository;
  private final ProvisioningMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public List<ApiKeySummaryDto> execute(UUID businessId) {
    return apiKeyRepository.findAllByBusinessIdAndRevokedFalse(businessId).stream()
        .map(mapper::toSummary)
        .toList();
  }
}
