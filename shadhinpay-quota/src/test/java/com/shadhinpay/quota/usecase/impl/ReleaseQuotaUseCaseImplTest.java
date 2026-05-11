package com.shadhinpay.quota.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shadhinpay.quota.cache.QuotaCachePort;
import java.time.Clock;
import java.util.UUID;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReleaseQuotaUseCaseImplTest {

  private QuotaCachePort cachePort;
  private ReleaseQuotaUseCaseImpl useCase;
  private LogCaptor logCaptor;

  @BeforeEach
  void setUp() {
    cachePort = mock(QuotaCachePort.class);
    useCase = new ReleaseQuotaUseCaseImpl(cachePort, Clock.systemUTC());
    logCaptor = LogCaptor.forClass(ReleaseQuotaUseCaseImpl.class);
  }

  @AfterEach
  void tearDown() {
    logCaptor.close();
  }

  @Test
  void testReleaseSuccess() {
    UUID merchantId = UUID.randomUUID();
    UUID reservationId = UUID.randomUUID();
    when(cachePort.releasePending(eq(merchantId), anyString(), eq(reservationId.toString())))
        .thenReturn(true);

    useCase.execute(merchantId, reservationId);

    verify(cachePort).releasePending(eq(merchantId), anyString(), eq(reservationId.toString()));
    assertThat(logCaptor.getWarnLogs()).isEmpty();
    assertThat(logCaptor.getErrorLogs()).isEmpty();
  }

  @Test
  void testRelease_NoOp() {
    UUID merchantId = UUID.randomUUID();
    UUID reservationId = UUID.randomUUID();
    when(cachePort.releasePending(eq(merchantId), anyString(), eq(reservationId.toString())))
        .thenReturn(false);

    useCase.execute(merchantId, reservationId);

    verify(cachePort).releasePending(eq(merchantId), anyString(), eq(reservationId.toString()));
    assertThat(logCaptor.getWarnLogs())
        .anyMatch(log -> log.contains("Release ignored. Reservation not found"));
  }

  @Test
  void testRelease_FailOpen() {
    UUID merchantId = UUID.randomUUID();
    UUID reservationId = UUID.randomUUID();
    when(cachePort.releasePending(eq(merchantId), anyString(), eq(reservationId.toString())))
        .thenThrow(new RuntimeException("Redis connection timeout"));

    useCase.execute(merchantId, reservationId);

    assertThat(logCaptor.getErrorLogs())
        .anyMatch(
            log -> log.contains("Failed to release reservation") && log.contains("Failing OPEN"));
  }
}
