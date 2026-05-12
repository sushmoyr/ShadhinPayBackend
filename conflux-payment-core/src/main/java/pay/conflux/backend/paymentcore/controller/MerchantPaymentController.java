package pay.conflux.backend.paymentcore.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.paymentcore.constant.PaymentCoreRoutes;
import pay.conflux.backend.paymentcore.dto.InitiatePaymentRestRequest;
import pay.conflux.backend.paymentcore.dto.PaymentResponseDto;

@Tag(name = "Payments - Merchant")
public interface MerchantPaymentController {

  @PostMapping(PaymentCoreRoutes.PAYMENTS)
  ResponseEntity<ApiResult<PaymentResponseDto>> initiate(
      String idempotencyKey, UUID businessId, InitiatePaymentRestRequest body);

  @GetMapping(PaymentCoreRoutes.PAYMENT_BY_ID)
  ResponseEntity<ApiResult<PaymentResponseDto>> getById(UUID businessId, UUID id);
}
