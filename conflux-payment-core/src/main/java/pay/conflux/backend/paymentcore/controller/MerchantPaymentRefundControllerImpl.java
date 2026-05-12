package pay.conflux.backend.paymentcore.controller;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.common.error.ErrorCode;
import pay.conflux.backend.paymentcore.dto.PaymentResponseDto;

@RestController
public class MerchantPaymentRefundControllerImpl implements MerchantPaymentRefundController {

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<PaymentResponseDto>> refund(@PathVariable("id") UUID id) {
    return ApiResult.error(
        HttpStatus.NOT_IMPLEMENTED,
        "Refund will land in payment-core 8b",
        ErrorCode.INTERNAL_ERROR);
  }
}
