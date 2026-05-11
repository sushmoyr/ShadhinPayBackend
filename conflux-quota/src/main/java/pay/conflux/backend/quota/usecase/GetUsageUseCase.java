package pay.conflux.backend.quota.usecase;

import java.util.UUID;

/**
 * Returns a merchant's quota state for a given billing period.
 *
 * <p><b>Caller:</b> the merchant dashboard backend in {@code identity}/{@code provisioning}, and
 * admin reporting flows.
 *
 * <p><b>Guarantees:</b>
 *
 * <ul>
 *   <li>Reads from the live Redis counters when available; falls back to the persisted {@code
 *       QuotaUsage} aggregate when reading historical periods.
 *   <li>Throws on malformed period strings (must match {@code YYYY-MM}).
 * </ul>
 */
public interface GetUsageUseCase {

  /**
   * Returns the merchant's quota state for the given period.
   *
   * @param merchantId merchant whose usage is being queried
   * @param period billing period in {@code YYYY-MM} form (e.g. {@code "2026-05"})
   * @return a {@link QuotaUsageView}; never {@code null}
   */
  QuotaUsageView execute(UUID merchantId, String period);
}
