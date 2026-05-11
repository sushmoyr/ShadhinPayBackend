package pay.conflux.backend.quota.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.quota.cache.QuotaCachePort;
import pay.conflux.backend.quota.config.QuotaConfig;
import pay.conflux.backend.quota.entity.QuotaUsage;
import pay.conflux.backend.quota.repository.QuotaUsageRepository;
import pay.conflux.backend.quota.usecase.QuotaUsageView;

class GetUsageUseCaseImplTest {

  private QuotaCachePort cachePort;
  private QuotaConfig quotaConfig;
  private QuotaUsageRepository repository;
  private GetUsageUseCaseImpl useCase;
  private LogCaptor logCaptor;

  @BeforeEach
  void setUp() {
    cachePort = mock(QuotaCachePort.class);
    quotaConfig = mock(QuotaConfig.class);
    repository = mock(QuotaUsageRepository.class);
    when(quotaConfig.getFreeQuotaPerMonth()).thenReturn(10);
    useCase = new GetUsageUseCaseImpl(cachePort, quotaConfig, repository);
    logCaptor = LogCaptor.forClass(GetUsageUseCaseImpl.class);
  }

  @AfterEach
  void tearDown() {
    logCaptor.close();
  }

  @Test
  void testGetUsageSuccess_Redis() {
    UUID merchantId = UUID.randomUUID();
    String period = "2024-05";
    when(cachePort.getFinalCount(merchantId, period)).thenReturn(7);

    QuotaUsageView result = useCase.execute(merchantId, period);

    assertThat(result.usedCount()).isEqualTo(7);
    assertThat(result.freeRemaining()).isEqualTo(3);
    assertThat(result.period()).isEqualTo(period);
  }

  @Test
  void testGetUsageSuccess_ExceedsFreeTier() {
    UUID merchantId = UUID.randomUUID();
    String period = "2024-05";
    when(cachePort.getFinalCount(merchantId, period)).thenReturn(12);

    QuotaUsageView result = useCase.execute(merchantId, period);

    assertThat(result.usedCount()).isEqualTo(12);
    assertThat(result.freeRemaining()).isEqualTo(0);
    assertThat(result.period()).isEqualTo(period);
  }

  @Test
  void testGetUsage_MalformedPeriod() {
    UUID merchantId = UUID.randomUUID();
    assertThatThrownBy(() -> useCase.execute(merchantId, "24-05"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Malformed period string");
  }

  @Test
  void testGetUsage_NullPeriod() {
    UUID merchantId = UUID.randomUUID();
    assertThatThrownBy(() -> useCase.execute(merchantId, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Malformed period string");
  }

  @Test
  void testGetUsage_InvalidMonth() {
    UUID merchantId = UUID.randomUUID();
    assertThatThrownBy(() -> useCase.execute(merchantId, "2024-13"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testGetUsage_RedisFailFallbackToDb_Found() {
    UUID merchantId = UUID.randomUUID();
    String period = "2024-05";
    when(cachePort.getFinalCount(merchantId, period)).thenThrow(new RuntimeException("Redis down"));

    QuotaUsage entity = new QuotaUsage(UUID.randomUUID(), merchantId, period);
    entity.setPartnerModeCount(8);
    when(repository.findByMerchantIdAndPeriod(merchantId, period)).thenReturn(Optional.of(entity));

    QuotaUsageView result = useCase.execute(merchantId, period);

    assertThat(result.usedCount()).isEqualTo(8);
    assertThat(result.freeRemaining()).isEqualTo(2);
    assertThat(logCaptor.getWarnLogs()).anyMatch(log -> log.contains("Falling back to DB"));
  }

  @Test
  void testGetUsage_RedisFailFallbackToDb_NotFound() {
    UUID merchantId = UUID.randomUUID();
    String period = "2024-05";
    when(cachePort.getFinalCount(merchantId, period)).thenThrow(new RuntimeException("Redis down"));
    when(repository.findByMerchantIdAndPeriod(merchantId, period)).thenReturn(Optional.empty());

    QuotaUsageView result = useCase.execute(merchantId, period);

    assertThat(result.usedCount()).isEqualTo(0);
    assertThat(result.freeRemaining()).isEqualTo(10);
  }
}
