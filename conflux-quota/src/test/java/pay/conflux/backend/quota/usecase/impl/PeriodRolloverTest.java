package pay.conflux.backend.quota.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pay.conflux.backend.quota.cache.QuotaCachePort;
import pay.conflux.backend.quota.config.QuotaConfig;

/**
 * Verifies that the use-case impls use the current system time to derive the period — i.e. on the
 * boundary between two months, two distinct Redis keys are touched. The period must never be
 * cached.
 */
class PeriodRolloverTest {

  @Test
  void reserve_atMonthBoundary_touchesTwoDistinctPeriods() {
    QuotaCachePort cachePort = mock(QuotaCachePort.class);
    QuotaConfig quotaConfig = mock(QuotaConfig.class);
    when(quotaConfig.getFreeQuotaPerMonth()).thenReturn(10);
    when(cachePort.reservePending(any(UUID.class), any(String.class)))
        .thenReturn(UUID.randomUUID().toString());

    UUID merchantId = UUID.randomUUID();

    Clock mayClock = Clock.fixed(Instant.parse("2026-05-31T23:59:59Z"), ZoneOffset.UTC);
    Clock junClock = Clock.fixed(Instant.parse("2026-06-01T00:00:01Z"), ZoneOffset.UTC);

    new ReserveQuotaUseCaseImpl(cachePort, quotaConfig, mayClock).execute(merchantId);
    new ReserveQuotaUseCaseImpl(cachePort, quotaConfig, junClock).execute(merchantId);

    ArgumentCaptor<String> periods = ArgumentCaptor.forClass(String.class);
    verify(cachePort, org.mockito.Mockito.times(2))
        .reservePending(eq(merchantId), periods.capture());

    assertThat(periods.getAllValues()).containsExactly("2026-05", "2026-06");
  }

  @Test
  void confirm_atMonthBoundary_addressesCorrectPeriod() {
    QuotaCachePort cachePort = mock(QuotaCachePort.class);
    when(cachePort.confirmReservation(any(UUID.class), any(String.class), any(String.class)))
        .thenReturn(true);

    UUID merchantId = UUID.randomUUID();
    UUID resId = UUID.randomUUID();

    Clock mayClock = Clock.fixed(Instant.parse("2026-05-31T23:59:59Z"), ZoneOffset.UTC);
    Clock junClock = Clock.fixed(Instant.parse("2026-06-01T00:00:01Z"), ZoneOffset.UTC);

    new ConfirmQuotaUseCaseImpl(cachePort, mayClock).execute(merchantId, resId);
    new ConfirmQuotaUseCaseImpl(cachePort, junClock).execute(merchantId, resId);

    verify(cachePort).confirmReservation(eq(merchantId), eq("2026-05"), eq(resId.toString()));
    verify(cachePort).confirmReservation(eq(merchantId), eq("2026-06"), eq(resId.toString()));
  }
}
