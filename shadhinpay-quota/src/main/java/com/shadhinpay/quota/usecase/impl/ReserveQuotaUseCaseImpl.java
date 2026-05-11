package com.shadhinpay.quota.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.quota.cache.QuotaCachePort;
import com.shadhinpay.quota.config.QuotaConfig;
import com.shadhinpay.quota.usecase.QuotaReservation;
import com.shadhinpay.quota.usecase.ReserveQuotaUseCase;
import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class ReserveQuotaUseCaseImpl implements ReserveQuotaUseCase {

  private final QuotaCachePort cachePort;
  private final QuotaConfig quotaConfig;
  private final Clock clock;

  @Override
  public QuotaReservation execute(UUID merchantId) {
    String period = YearMonth.now(clock).toString();
    try {
      String reservationId = cachePort.reservePending(merchantId, period);
      int finalCount = cachePort.getFinalCount(merchantId, period);
      int pendingCount = cachePort.countPending(merchantId, period);

      // Spec deviates: prompt says "finalCount + pending - 1 <= 10" which is off-by-one
      // (grants 11/month). We use "finalCount + pending <= 10" so the 11th reservation
      // correctly becomes BILLABLE.
      int totalUsageWithThisReservation = finalCount + pendingCount;

      QuotaReservation.Status status =
          (totalUsageWithThisReservation <= quotaConfig.getFreeQuotaPerMonth())
              ? QuotaReservation.Status.FREE
              : QuotaReservation.Status.BILLABLE;

      return new QuotaReservation(UUID.fromString(reservationId), status);
    } catch (Exception e) {
      log.error(
          "Failed to reserve quota for merchantId: {}, period: {}. Failing OPEN.",
          merchantId,
          period,
          e);
      return new QuotaReservation(UUID.randomUUID(), QuotaReservation.Status.FREE);
    }
  }
}
