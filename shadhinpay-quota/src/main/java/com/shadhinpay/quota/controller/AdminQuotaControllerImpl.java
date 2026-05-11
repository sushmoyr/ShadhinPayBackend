package com.shadhinpay.quota.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.quota.controller.dto.AdminQuotaUsageDto;
import com.shadhinpay.quota.usecase.GetUsageUseCase;
import com.shadhinpay.quota.usecase.QuotaUsageView;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminQuotaControllerImpl implements AdminQuotaController {

  private final GetUsageUseCase getUsageUseCase;

  @Override
  public ResponseEntity<ApiResult<AdminQuotaUsageDto>> getQuota(
      @RequestParam("merchantId") UUID merchantId, @RequestParam("period") String period) {

    QuotaUsageView view = getUsageUseCase.execute(merchantId, period);

    AdminQuotaUsageDto dto =
        new AdminQuotaUsageDto(merchantId, view.usedCount(), view.freeRemaining(), view.period());
    return ApiResult.ok(dto);
  }
}
