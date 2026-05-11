package pay.conflux.backend.quota.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.quota.constant.QuotaRoutes;
import pay.conflux.backend.quota.controller.dto.QuotaUsageDto;

@RequestMapping(QuotaRoutes.MERCHANT_USAGE)
public interface MerchantQuotaController {

  @GetMapping
  ResponseEntity<ApiResult<QuotaUsageDto>> getUsage();
}
