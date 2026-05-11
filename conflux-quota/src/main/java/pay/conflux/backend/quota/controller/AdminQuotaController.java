package pay.conflux.backend.quota.controller;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.quota.constant.QuotaRoutes;
import pay.conflux.backend.quota.controller.dto.AdminQuotaUsageDto;

@RequestMapping(QuotaRoutes.ADMIN_QUOTA)
public interface AdminQuotaController {

  @GetMapping
  ResponseEntity<ApiResult<AdminQuotaUsageDto>> getQuota(
      @RequestParam("merchantId") UUID merchantId, @RequestParam("period") String period);
}
