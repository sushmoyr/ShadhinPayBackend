package pay.conflux.backend.paymentcore.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.common.error.ForbiddenException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.paymentcore.constant.PaymentCoreRoutes;
import pay.conflux.backend.paymentcore.dto.RefundPaymentResponseDto;
import pay.conflux.backend.paymentcore.dto.RefundPaymentRestRequest;
import pay.conflux.backend.paymentcore.entity.Transaction;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.usecase.RefundPaymentRequest;
import pay.conflux.backend.paymentcore.usecase.RefundPaymentResult;
import pay.conflux.backend.paymentcore.usecase.RefundPaymentUseCase;

@RestController
@RequiredArgsConstructor
public class MerchantPaymentRefundControllerImpl implements MerchantPaymentRefundController {

  private final RefundPaymentUseCase refundPaymentUseCase;
  private final TransactionRepository transactionRepository;

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<RefundPaymentResponseDto>> refund(
      @PathVariable("id") UUID id,
      @RequestHeader(PaymentCoreRoutes.HEADER_BUSINESS_ID) UUID businessId,
      @RequestBody @Valid RefundPaymentRestRequest body) {

    Transaction original =
        transactionRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
    if (!original.getBusinessId().equals(businessId)) {
      throw new ForbiddenException("Transaction does not belong to the requesting business");
    }

    String currency =
        body.getCurrency() == null || body.getCurrency().isBlank()
            ? original.getAmountCurrency()
            : body.getCurrency();
    RefundPaymentRequest useCaseRequest =
        new RefundPaymentRequest(id, new Money(body.getAmount(), currency), body.getReason());
    RefundPaymentResult result = refundPaymentUseCase.execute(useCaseRequest);

    RefundPaymentResponseDto dto =
        new RefundPaymentResponseDto(
            result.refundTransactionId(), result.originalTransactionId(), result.status());
    return ApiResult.ok(dto);
  }
}
