package pay.conflux.backend.quota.usecase;

import java.util.Objects;

/**
 * Read-only snapshot of a merchant's quota state for a given billing period.
 *
 * <p>Returned by {@link GetUsageUseCase}. Counts confirmed (final) reservations only — pending
 * soft-reservations are not surfaced to callers, so the figure is stable against in-flight payments
 * that may still release.
 *
 * @param usedCount transactions consumed against the free + billable tiers in {@link #period}
 * @param freeRemaining transactions still available in the free tier; {@code 0} once exhausted
 * @param period billing period in {@code YYYY-MM} form (e.g. {@code "2026-05"})
 */
public record QuotaUsageView(int usedCount, int freeRemaining, String period) {

  public QuotaUsageView {
    Objects.requireNonNull(period, "period must not be null");
    if (usedCount < 0) {
      throw new IllegalArgumentException("usedCount must not be negative");
    }
    if (freeRemaining < 0) {
      throw new IllegalArgumentException("freeRemaining must not be negative");
    }
  }
}
