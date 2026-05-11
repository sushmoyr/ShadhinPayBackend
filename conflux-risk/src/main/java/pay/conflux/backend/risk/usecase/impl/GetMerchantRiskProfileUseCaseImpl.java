package pay.conflux.backend.risk.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.risk.dto.MerchantRiskProfileDto;
import pay.conflux.backend.risk.mapper.MerchantRiskProfileMapper;
import pay.conflux.backend.risk.repository.MerchantRiskProfileRepository;
import pay.conflux.backend.risk.usecase.internal.GetMerchantRiskProfileUseCase;

@UseCase
@RequiredArgsConstructor
public class GetMerchantRiskProfileUseCaseImpl implements GetMerchantRiskProfileUseCase {

  private final MerchantRiskProfileRepository merchantRiskProfileRepository;
  private final MerchantRiskProfileMapper merchantRiskProfileMapper;

  @Override
  @Transactional(readOnly = true)
  public MerchantRiskProfileDto execute(UUID merchantId) {
    return merchantRiskProfileRepository
        .findByMerchantId(merchantId)
        .map(merchantRiskProfileMapper::toDto)
        .orElseThrow(() -> new ResourceNotFoundException("MerchantRiskProfile", merchantId));
  }
}
