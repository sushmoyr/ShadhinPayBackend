package pay.conflux.backend.paymentcore.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.paymentcore.constant.PaymentCoreRoutes;
import pay.conflux.backend.paymentcore.dto.RefundPaymentResponseDto;
import pay.conflux.backend.paymentcore.dto.RefundPaymentRestRequest;

@Tag(name = "Payments - Merchant")
public interface MerchantPaymentRefundController {

  @PostMapping(PaymentCoreRoutes.PAYMENT_REFUND)
  ResponseEntity<ApiResult<RefundPaymentResponseDto>> refund(
      UUID id, UUID businessId, RefundPaymentRestRequest body);
}
