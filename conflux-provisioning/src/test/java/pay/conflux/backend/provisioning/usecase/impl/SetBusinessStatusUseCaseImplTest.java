package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.dto.BusinessDto;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.mapper.ProvisioningMapper;
import pay.conflux.backend.provisioning.repository.BusinessRepository;

@ExtendWith(MockitoExtension.class)
class SetBusinessStatusUseCaseImplTest {

  @Mock private BusinessRepository businessRepository;
  @Mock private ProvisioningMapper mapper;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private SetOperations<String, String> setOps;

  @InjectMocks private SetBusinessStatusUseCaseImpl useCase;

  @Test
  void execute_deactivate_evictsApiKeyCache() {
    UUID businessId = UUID.randomUUID();
    Business business = new Business();
    business.setId(businessId);
    business.setStatus(BusinessStatus.ACTIVE);
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
    when(businessRepository.save(business)).thenReturn(business);
    when(mapper.toDto(business)).thenReturn(new BusinessDto());
    when(redisTemplate.opsForSet()).thenReturn(setOps);
    when(setOps.members(ProvisioningCacheKeys.businessApiKeyIndex(businessId)))
        .thenReturn(Set.of("h1", "h2"));

    useCase.execute(businessId, BusinessStatus.INACTIVE);

    verify(redisTemplate).delete(ProvisioningCacheKeys.apiKey("h1"));
    verify(redisTemplate).delete(ProvisioningCacheKeys.apiKey("h2"));
    verify(redisTemplate).delete(ProvisioningCacheKeys.businessApiKeyIndex(businessId));
  }

  @Test
  void execute_activate_doesNotTouchCache() {
    UUID businessId = UUID.randomUUID();
    Business business = new Business();
    business.setId(businessId);
    business.setStatus(BusinessStatus.INACTIVE);
    when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
    when(businessRepository.save(business)).thenReturn(business);
    when(mapper.toDto(business)).thenReturn(new BusinessDto());

    useCase.execute(businessId, BusinessStatus.ACTIVE);

    verify(redisTemplate, never()).delete(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void execute_businessMissing_throwsResourceNotFound() {
    UUID businessId = UUID.randomUUID();
    when(businessRepository.findById(businessId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(businessId, BusinessStatus.INACTIVE))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
