package pay.conflux.backend.quota.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.quota.controller.dto.AdminQuotaUsageDto;
import pay.conflux.backend.quota.usecase.GetUsageUseCase;
import pay.conflux.backend.quota.usecase.QuotaUsageView;

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
