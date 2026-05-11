package pay.conflux.backend.quota.usecase.impl;

import java.time.YearMonth;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.quota.cache.QuotaCachePort;
import pay.conflux.backend.quota.config.QuotaConfig;
import pay.conflux.backend.quota.entity.QuotaUsage;
import pay.conflux.backend.quota.repository.QuotaUsageRepository;
import pay.conflux.backend.quota.usecase.GetUsageUseCase;
import pay.conflux.backend.quota.usecase.QuotaUsageView;

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
