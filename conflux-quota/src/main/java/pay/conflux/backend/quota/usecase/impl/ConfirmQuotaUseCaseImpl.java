package pay.conflux.backend.quota.usecase.impl;

import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.quota.cache.QuotaCachePort;
import pay.conflux.backend.quota.usecase.ConfirmQuotaUseCase;

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
