package com.shadhinpay.quota.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.quota.cache.QuotaCachePort;
import com.shadhinpay.quota.usecase.ReleaseQuotaUseCase;
import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class ReleaseQuotaUseCaseImpl implements ReleaseQuotaUseCase {

  private final QuotaCachePort cachePort;
  private final Clock clock;

  @Override
  public void execute(UUID merchantId, UUID reservationId) {
    String period = YearMonth.now(clock).toString();
    try {
      boolean released = cachePort.releasePending(merchantId, period, reservationId.toString());
      if (!released) {
        log.warn(
            "Release ignored. Reservation not found for merchantId: {}, period: {}, reservationId:"
                + " {}",
            merchantId,
            period,
            reservationId);
      }
    } catch (Exception e) {
      log.error(
          "Failed to release reservation for merchantId: {}, period: {}, reservationId: {}. Failing"
              + " OPEN.",
          merchantId,
          period,
          reservationId,
          e);
    }
  }
}
