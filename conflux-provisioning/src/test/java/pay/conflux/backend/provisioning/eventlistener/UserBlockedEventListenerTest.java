package pay.conflux.backend.provisioning.eventlistener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import pay.conflux.backend.identity.events.UserBlockedEvent;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.impl.ProvisioningCacheKeys;

@ExtendWith(MockitoExtension.class)
class UserBlockedEventListenerTest {

  @Mock private BusinessRepository businessRepository;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private SetOperations<String, String> setOps;

  private UserBlockedEventListener listener;

  @BeforeEach
  void setUp() {
    listener = new UserBlockedEventListener(businessRepository, redisTemplate);
  }

  @Test
  void on_block_setsBusinessInactive_andEvictsCachesViaSecondaryIndex() {
    UUID userId = UUID.randomUUID();
    UUID businessId = UUID.randomUUID();
    Business business = new Business();
    business.setId(businessId);
    business.setMerchantId(userId);
    business.setStatus(BusinessStatus.ACTIVE);

    when(businessRepository.findByMerchantIdAndDeletedFalse(userId)).thenReturn(List.of(business));
    when(businessRepository.save(business)).thenReturn(business);
    when(redisTemplate.opsForSet()).thenReturn(setOps);
    when(setOps.members(ProvisioningCacheKeys.businessApiKeyIndex(businessId)))
        .thenReturn(Set.of("hashA", "hashB"));
    when(setOps.members(ProvisioningCacheKeys.businessVendorIndex(businessId)))
        .thenReturn(Set.of("BKASH", "NAGAD"));

    listener.on(new UserBlockedEvent(userId, "fraud", Instant.now(), "trace-x"));

    assertThat(business.getStatus()).isEqualTo(BusinessStatus.INACTIVE);

    ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(redisTemplate, org.mockito.Mockito.atLeast(4))
        .delete(keys.capture());
    assertThat(keys.getAllValues())
        .contains(
            ProvisioningCacheKeys.apiKey("hashA"),
            ProvisioningCacheKeys.apiKey("hashB"),
            ProvisioningCacheKeys.businessApiKeyIndex(businessId),
            ProvisioningCacheKeys.vendorConfig(businessId, "BKASH"),
            ProvisioningCacheKeys.vendorConfig(businessId, "NAGAD"),
            ProvisioningCacheKeys.businessVendorIndex(businessId));
  }

  @Test
  void on_block_noBusinesses_doesNotTouchRedis() {
    UUID userId = UUID.randomUUID();
    when(businessRepository.findByMerchantIdAndDeletedFalse(userId)).thenReturn(List.of());

    listener.on(new UserBlockedEvent(userId, "fraud", Instant.now(), "trace-y"));

    org.mockito.Mockito.verifyNoInteractions(redisTemplate);
  }
}
