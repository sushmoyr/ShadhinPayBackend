package com.shadhinpay.quota.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.quota.constant.QuotaRoutes;
import com.shadhinpay.quota.controller.dto.AdminQuotaUsageDto;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping(QuotaRoutes.ADMIN_QUOTA)
public interface AdminQuotaController {

  @GetMapping
  ResponseEntity<ApiResult<AdminQuotaUsageDto>> getQuota(
      @RequestParam("merchantId") UUID merchantId, @RequestParam("period") String period);
}
