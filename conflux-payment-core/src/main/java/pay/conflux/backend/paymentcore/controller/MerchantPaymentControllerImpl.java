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
import pay.conflux.backend.paymentcore.dto.InitiatePaymentRestRequest;
import pay.conflux.backend.paymentcore.dto.PaymentResponseDto;
import pay.conflux.backend.paymentcore.entity.Transaction;
import pay.conflux.backend.paymentcore.mapper.TransactionMapper;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.usecase.InitiatePaymentRequest;
import pay.conflux.backend.paymentcore.usecase.InitiatePaymentUseCase;
import pay.conflux.backend.paymentcore.usecase.PaymentInitiationResult;

/**
 * Public REST adapter for the merchant payment surface. The auth model is filter-provided (Wave B
 * 8c wires the API-key filter that populates {@code SecurityContextHolder} and the {@code
 * X-Business-Id} header); this 8a stub trusts the header so slice tests can drive it directly.
 */
@RestController
@RequiredArgsConstructor
public class MerchantPaymentControllerImpl implements MerchantPaymentController {

  private final InitiatePaymentUseCase initiatePaymentUseCase;
  private final TransactionRepository transactionRepository;
  private final TransactionMapper transactionMapper;

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<PaymentResponseDto>> initiate(
      @RequestHeader(PaymentCoreRoutes.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
      @RequestHeader(PaymentCoreRoutes.HEADER_BUSINESS_ID) UUID businessId,
      @RequestBody @Valid InitiatePaymentRestRequest body) {

    InitiatePaymentRequest request =
        new InitiatePaymentRequest(
            businessId,
            new Money(body.getAmount(), body.getCurrency()),
            body.getVendor(),
            body.getMerchantOrderReference(),
            body.getCallbackUrl() == null ? "" : body.getCallbackUrl(),
            body.getWebhookUrl() == null ? "" : body.getWebhookUrl(),
            body.getMetadata(),
            idempotencyKey);
    PaymentInitiationResult result = initiatePaymentUseCase.execute(request);

    PaymentResponseDto response = new PaymentResponseDto();
    response.setTransactionId(result.transactionId());
    response.setStatus(result.status());
    response.setRedirectUrl(result.redirectUrl());
    response.setAmount(body.getAmount());
    response.setCurrency(body.getCurrency());
    response.setVendor(body.getVendor().toUpperCase());
    response.setMerchantOrderReference(body.getMerchantOrderReference());
    return ApiResult.created(response);
  }

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<PaymentResponseDto>> getById(
      @RequestHeader(PaymentCoreRoutes.HEADER_BUSINESS_ID) UUID businessId,
      @PathVariable("id") UUID id) {
    Transaction transaction =
        transactionRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
    if (!transaction.getBusinessId().equals(businessId)) {
      throw new ForbiddenException("Transaction does not belong to the requesting business");
    }
    return ApiResult.ok(transactionMapper.toResponseDto(transaction));
  }
}
