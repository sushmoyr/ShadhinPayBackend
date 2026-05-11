package pay.conflux.backend.quota.controller;

import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.common.security.SecurityUtils;
import pay.conflux.backend.quota.controller.dto.QuotaUsageDto;
import pay.conflux.backend.quota.usecase.GetUsageUseCase;
import pay.conflux.backend.quota.usecase.QuotaUsageView;

@RestController
@RequiredArgsConstructor
public class MerchantQuotaControllerImpl implements MerchantQuotaController {

  private final GetUsageUseCase getUsageUseCase;
  private final Clock clock;

  @Override
  public ResponseEntity<ApiResult<QuotaUsageDto>> getUsage() {
    UUID merchantId =
        SecurityUtils.currentMerchantId()
            .orElseThrow(() -> new UnauthorizedException("No merchant context found"));

    String currentPeriod = YearMonth.now(clock).toString();
    QuotaUsageView view = getUsageUseCase.execute(merchantId, currentPeriod);

    QuotaUsageDto dto = new QuotaUsageDto(view.usedCount(), view.freeRemaining(), view.period());
    return ApiResult.ok(dto);
  }
}
