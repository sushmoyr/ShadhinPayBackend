package pay.conflux.backend.quota.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.UUID;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.quota.cache.QuotaCachePort;
import pay.conflux.backend.quota.config.QuotaConfig;
import pay.conflux.backend.quota.usecase.QuotaReservation;

class ReserveQuotaUseCaseImplTest {

  private QuotaCachePort cachePort;
  private QuotaConfig quotaConfig;
  private ReserveQuotaUseCaseImpl useCase;
  private LogCaptor logCaptor;

  @BeforeEach
  void setUp() {
    cachePort = mock(QuotaCachePort.class);
    quotaConfig = mock(QuotaConfig.class);
    when(quotaConfig.getFreeQuotaPerMonth()).thenReturn(10);
    useCase = new ReserveQuotaUseCaseImpl(cachePort, quotaConfig, Clock.systemUTC());
    logCaptor = LogCaptor.forClass(ReserveQuotaUseCaseImpl.class);
  }

  @AfterEach
  void tearDown() {
    logCaptor.close();
  }

  @Test
  void testReserveSuccess_FreeTier() {
    UUID merchantId = UUID.randomUUID();
    String reservationId = UUID.randomUUID().toString();
    when(cachePort.reservePending(eq(merchantId), anyString())).thenReturn(reservationId);
    when(cachePort.getFinalCount(eq(merchantId), anyString())).thenReturn(5);
    when(cachePort.countPending(eq(merchantId), anyString())).thenReturn(3);

    QuotaReservation result = useCase.execute(merchantId);

    assertThat(result.status()).isEqualTo(QuotaReservation.Status.FREE);
    assertThat(result.reservationId().toString()).isEqualTo(reservationId);
  }

  @Test
  void testReserveSuccess_BillableTier() {
    UUID merchantId = UUID.randomUUID();
    String reservationId = UUID.randomUUID().toString();
    when(cachePort.reservePending(eq(merchantId), anyString())).thenReturn(reservationId);
    when(cachePort.getFinalCount(eq(merchantId), anyString())).thenReturn(9);
    when(cachePort.countPending(eq(merchantId), anyString())).thenReturn(2);

    QuotaReservation result = useCase.execute(merchantId);

    assertThat(result.status()).isEqualTo(QuotaReservation.Status.BILLABLE);
    assertThat(result.reservationId().toString()).isEqualTo(reservationId);
  }

  @Test
  void testReserve_BoundaryAt10_IsFree() {
    UUID merchantId = UUID.randomUUID();
    String reservationId = UUID.randomUUID().toString();
    when(cachePort.reservePending(eq(merchantId), anyString())).thenReturn(reservationId);
    when(cachePort.getFinalCount(eq(merchantId), anyString())).thenReturn(9);
    when(cachePort.countPending(eq(merchantId), anyString())).thenReturn(1);

    QuotaReservation result = useCase.execute(merchantId);

    assertThat(result.status()).isEqualTo(QuotaReservation.Status.FREE);
  }

  @Test
  void testReserve_BoundaryAt11_IsBillable() {
    UUID merchantId = UUID.randomUUID();
    String reservationId = UUID.randomUUID().toString();
    when(cachePort.reservePending(eq(merchantId), anyString())).thenReturn(reservationId);
    when(cachePort.getFinalCount(eq(merchantId), anyString())).thenReturn(10);
    when(cachePort.countPending(eq(merchantId), anyString())).thenReturn(1);

    QuotaReservation result = useCase.execute(merchantId);

    assertThat(result.status()).isEqualTo(QuotaReservation.Status.BILLABLE);
  }

  @Test
  void testReserve_FailOpen() {
    UUID merchantId = UUID.randomUUID();
    when(cachePort.reservePending(eq(merchantId), anyString()))
        .thenThrow(new RuntimeException("Redis connection timeout"));

    QuotaReservation result = useCase.execute(merchantId);

    assertThat(result.status()).isEqualTo(QuotaReservation.Status.FREE);
    assertThat(result.reservationId()).isNotNull();

    assertThat(logCaptor.getErrorLogs())
        .anyMatch(
            log ->
                log.contains("Failed to reserve quota for merchantId")
                    && log.contains("Failing OPEN"));
  }
}
