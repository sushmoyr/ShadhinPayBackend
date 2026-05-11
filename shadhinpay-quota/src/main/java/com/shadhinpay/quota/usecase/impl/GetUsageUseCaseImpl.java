package com.shadhinpay.quota.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.quota.cache.QuotaCachePort;
import com.shadhinpay.quota.config.QuotaConfig;
import com.shadhinpay.quota.entity.QuotaUsage;
import com.shadhinpay.quota.repository.QuotaUsageRepository;
import com.shadhinpay.quota.usecase.GetUsageUseCase;
import com.shadhinpay.quota.usecase.QuotaUsageView;
import java.time.YearMonth;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class GetUsageUseCaseImpl implements GetUsageUseCase {

  private final QuotaCachePort cachePort;
  private final QuotaConfig quotaConfig;
  private final QuotaUsageRepository quotaUsageRepository;

  private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");

  @Override
  public QuotaUsageView execute(UUID merchantId, String period) {
    if (period == null || !PERIOD_PATTERN.matcher(period).matches()) {
      throw new IllegalArgumentException("Malformed period string. Must match YYYY-MM");
    }

    // YearMonth.parse() throws on a real-world-invalid month (e.g. "2024-13") that the
    // regex above can't catch; the parsed value is intentionally unused.
    @SuppressWarnings("PMD.UnusedLocalVariable")
    YearMonth parsed;
    try {
      parsed = YearMonth.parse(period);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid period: " + period, e);
    }
    log.trace("Parsed period {}", parsed);

    try {
      int finalCount = cachePort.getFinalCount(merchantId, period);
      int freeRemaining = Math.max(0, quotaConfig.getFreeQuotaPerMonth() - finalCount);
      return new QuotaUsageView(finalCount, freeRemaining, period);
    } catch (Exception e) {
      log.warn(
          "Failed to get usage from Redis for merchantId: {}, period: {}. Falling back to DB.",
          merchantId,
          period,
          e);
      return quotaUsageRepository
          .findByMerchantIdAndPeriod(merchantId, period)
          .map(this::fromEntity)
          .orElseGet(() -> new QuotaUsageView(0, quotaConfig.getFreeQuotaPerMonth(), period));
    }
  }

  private QuotaUsageView fromEntity(QuotaUsage entity) {
    int totalUsed = entity.getPartnerModeCount();
    int freeRemaining = Math.max(0, quotaConfig.getFreeQuotaPerMonth() - totalUsed);
    return new QuotaUsageView(totalUsed, freeRemaining, entity.getPeriod());
  }
}
