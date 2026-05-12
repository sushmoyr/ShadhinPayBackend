package pay.conflux.backend.paymentcore.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.dto.ApiResult;
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
 * Public REST adapter for the merchant payment surface. The auth model is filter-provided: Wave B
 * 8c installs {@code ApiKeyAuthFilter} which populates {@link
 * org.springframework.security.core.context.SecurityContextHolder} with the {@code MERCHANT}
 * authority and exposes the resolved {@code businessId} as the {@code X-Business-Id} request
 * attribute, which this controller reads via {@code @RequestAttribute}.
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
      @RequestAttribute(PaymentCoreRoutes.HEADER_BUSINESS_ID) UUID businessId,
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
    response.setVendor(body.getVendor().toUpperCase(java.util.Locale.ROOT));
    response.setMerchantOrderReference(body.getMerchantOrderReference());
    return ApiResult.created(response);
  }

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<PaymentResponseDto>> getById(
      @RequestAttribute(PaymentCoreRoutes.HEADER_BUSINESS_ID) UUID businessId,
      @PathVariable("id") UUID id) {
    Transaction transaction =
        transactionRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
    if (!transaction.getBusinessId().equals(businessId)) {
      // 404 (not 403) to avoid leaking the existence of another tenant's resource.
      throw new ResourceNotFoundException("Transaction", id);
    }
    return ApiResult.ok(transactionMapper.toResponseDto(transaction));
  }
}
