package com.shadhinpay.quota.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.quota.cache.QuotaCachePort;
import com.shadhinpay.quota.usecase.ConfirmQuotaUseCase;
import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class ConfirmQuotaUseCaseImpl implements ConfirmQuotaUseCase {

  private final QuotaCachePort cachePort;
  private final Clock clock;

  @Override
  public void execute(UUID merchantId, UUID reservationId) {
    String period = YearMonth.now(clock).toString();
    try {
      boolean confirmed =
          cachePort.confirmReservation(merchantId, period, reservationId.toString());
      if (!confirmed) {
        log.warn(
            "Confirm ignored. Reservation not found or expired for merchantId: {}, period: {},"
                + " reservationId: {}",
            merchantId,
            period,
            reservationId);
      }
    } catch (Exception e) {
      log.error(
          "Failed to confirm reservation for merchantId: {}, period: {}, reservationId: {}. Failing"
              + " OPEN.",
          merchantId,
          period,
          reservationId,
          e);
    }
  }
}
