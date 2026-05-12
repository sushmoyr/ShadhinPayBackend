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
import pay.conflux.backend.provisioning.constant.Environment;
import pay.conflux.backend.provisioning.dto.ApiKeySummaryDto;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;

@ExtendWith(MockitoExtension.class)
class ListApiKeysUseCaseImplTest {

  @Mock private ApiKeyRepository apiKeyRepository;
  @Mock private ProvisioningMapper mapper;
  @InjectMocks private ListApiKeysUseCaseImpl useCase;

  @Test
  void execute_returnsMappedNonRevokedKeys() {
    UUID businessId = UUID.randomUUID();
    ApiKey key = new ApiKey();
    key.setId(UUID.randomUUID());
    key.setBusinessId(businessId);
    key.setEnvironment(Environment.TEST);
    key.setKeyPrefix("sp_test_");
    key.setLastFour("a1b2");
    ApiKeySummaryDto dto = new ApiKeySummaryDto();
    dto.setId(key.getId());
    when(apiKeyRepository.findAllByBusinessIdAndRevokedFalse(businessId)).thenReturn(List.of(key));
    when(mapper.toSummary(key)).thenReturn(dto);

    List<ApiKeySummaryDto> result = useCase.execute(businessId);

    assertThat(result).containsExactly(dto);
  }

  @Test
  void execute_returnsEmptyWhenNoKeys() {
    UUID businessId = UUID.randomUUID();
    when(apiKeyRepository.findAllByBusinessIdAndRevokedFalse(businessId)).thenReturn(List.of());

    assertThat(useCase.execute(businessId)).isEmpty();
  }
}
