package com.shadhinpay.quota.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.common.error.UnauthorizedException;
import com.shadhinpay.common.security.SecurityUtils;
import com.shadhinpay.quota.controller.dto.QuotaUsageDto;
import com.shadhinpay.quota.usecase.GetUsageUseCase;
import com.shadhinpay.quota.usecase.QuotaUsageView;
import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

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
