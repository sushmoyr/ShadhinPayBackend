package com.shadhinpay.quota.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.quota.constant.QuotaRoutes;
import com.shadhinpay.quota.controller.dto.QuotaUsageDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping(QuotaRoutes.MERCHANT_USAGE)
public interface MerchantQuotaController {

  @GetMapping
  ResponseEntity<ApiResult<QuotaUsageDto>> getUsage();
}
