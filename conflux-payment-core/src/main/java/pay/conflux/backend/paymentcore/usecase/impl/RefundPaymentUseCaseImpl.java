package pay.conflux.backend.paymentcore.usecase.impl;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.adapters.port.PaymentProvider;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;
import pay.conflux.backend.adapters.port.VendorRefundRequest;
import pay.conflux.backend.adapters.port.VendorResponse;
import pay.conflux.backend.adapters.support.PaymentProviderRegistry;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.paymentcore.entity.Transaction;
import pay.conflux.backend.paymentcore.entity.TransactionStatus;
import pay.conflux.backend.paymentcore.entity.WebhookEventType;
import pay.conflux.backend.paymentcore.entity.WebhookOutbox;
import pay.conflux.backend.paymentcore.entity.WebhookOutboxStatus;
import pay.conflux.backend.paymentcore.events.PaymentRefundedEvent;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.repository.WebhookOutboxRepository;
import pay.conflux.backend.paymentcore.usecase.RefundPaymentRequest;
import pay.conflux.backend.paymentcore.usecase.RefundPaymentResult;
import pay.conflux.backend.paymentcore.usecase.RefundPaymentUseCase;
import pay.conflux.backend.provisioning.usecase.CredentialsResolver;

/**
 * Refunds an existing completed transaction. The refund is recorded as a separate {@code
 * Transaction} row so the ledger has a 1:1 mapping between refund payouts and platform postings.
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class RefundPaymentUseCaseImpl implements RefundPaymentUseCase {

  private final TransactionRepository transactionRepository;
  private final WebhookOutboxRepository webhookOutboxRepository;
  private final PaymentProviderRegistry paymentProviderRegistry;
  private final CredentialsResolver credentialsResolver;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public RefundPaymentResult execute(RefundPaymentRequest request) {
    Transaction original =
        transactionRepository
            .findById(request.originalTransactionId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("Transaction", request.originalTransactionId()));

    if (original.getStatus() != TransactionStatus.COMPLETED) {
      throw new InvalidOperationStateException(
          "Only COMPLETED transactions can be refunded; current status is " + original.getStatus());
    }
    if (!original.getAmountCurrency().equals(request.amount().currency())) {
      throw new ValidationException(
          "Refund currency must match original ("
              + original.getAmountCurrency()
              + ") but got "
              + request.amount().currency());
    }
    if (!request.amount().isPositive()) {
      throw new ValidationException("Refund amount must be positive");
    }
    if (request.amount().amount().compareTo(original.getAmountValue()) > 0) {
      throw new ValidationException("Refund amount exceeds the original transaction amount");
    }

    Vendor vendorEnum;
    try {
      vendorEnum = Vendor.valueOf(original.getVendor());
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Unsupported vendor: " + original.getVendor(), e);
    }

    Transaction refund =
        Transaction.builder()
            .businessId(original.getBusinessId())
            .merchantId(original.getMerchantId())
            .amountValue(request.amount().amount())
            .amountCurrency(request.amount().currency())
            .status(TransactionStatus.PENDING)
            .vendor(original.getVendor())
            .mode(original.getMode())
            .merchantOrderReference(original.getMerchantOrderReference() + "-refund")
            .callbackUrl(original.getCallbackUrl())
            .webhookUrl(original.getWebhookUrl())
            .metadata(buildRefundMetadata(original, request))
            .retryCount(0)
            .build();
    refund = transactionRepository.save(refund);

    VendorResponse vendorResponse;
    try {
      PaymentProvider provider = paymentProviderRegistry.lookup(vendorEnum);
      VendorCredentials creds =
          new VendorCredentials(
              credentialsResolver.resolveCredentials(
                  original.getBusinessId(), original.getVendor()));
      VendorRefundRequest vendorRequest =
          new VendorRefundRequest(
              refund.getId(),
              original.getVendorTransactionId() == null ? "" : original.getVendorTransactionId(),
              request.amount(),
              request.reason());
      vendorResponse = provider.refund(vendorRequest, creds);
    } catch (RuntimeException e) {
      log.warn(
          "Refund dispatch threw — leaving refund leg in PENDING_RECOVERY [refundId={},"
              + " originalId={}, vendor={}, traceId={}]",
          refund.getId(),
          original.getId(),
          original.getVendor(),
          traceId(),
          e);
      refund.setStatus(TransactionStatus.PENDING_RECOVERY);
      refund = transactionRepository.save(refund);
      return new RefundPaymentResult(
          refund.getId(), original.getId(), TransactionStatus.PENDING_RECOVERY.name());
    }

    refund = applyVendorResponse(refund, vendorResponse);

    if (refund.getStatus() == TransactionStatus.COMPLETED) {
      eventPublisher.publishEvent(
          new PaymentRefundedEvent(
              refund.getId(),
              original.getId(),
              request.amount(),
              refund.getMetadata() == null ? Map.of() : Map.copyOf(refund.getMetadata()),
              Instant.now(),
              traceId()));
      enqueueRefundWebhook(refund, original, request);
    }

    return new RefundPaymentResult(refund.getId(), original.getId(), refund.getStatus().name());
  }

  private Transaction applyVendorResponse(Transaction refund, VendorResponse response) {
    switch (response.status()) {
      case COMPLETED -> refund.setStatus(TransactionStatus.COMPLETED);
      case INITIATED, PENDING -> refund.setStatus(TransactionStatus.PENDING);
      case FAILED -> refund.setStatus(TransactionStatus.FAILED);
      case CANCELLED -> refund.setStatus(TransactionStatus.CANCELLED);
      case UNKNOWN -> refund.setStatus(TransactionStatus.PENDING_RECOVERY);
      default -> throw new IllegalStateException("Unhandled vendor status: " + response.status());
    }
    if (response.vendorTrxId() != null) {
      refund.setVendorTransactionId(response.vendorTrxId());
    }
    return transactionRepository.save(refund);
  }

  private Map<String, String> buildRefundMetadata(Transaction original, RefundPaymentRequest req) {
    Map<String, String> metadata = new LinkedHashMap<>();
    if (original.getMetadata() != null) {
      metadata.putAll(original.getMetadata());
    }
    metadata.put("refund_of", original.getId().toString());
    metadata.put("refund_reason", req.reason());
    return metadata;
  }

  private void enqueueRefundWebhook(
      Transaction refund, Transaction original, RefundPaymentRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("transactionId", refund.getId().toString());
    payload.put("originalTransactionId", original.getId().toString());
    payload.put("status", refund.getStatus().name());
    payload.put("amount", refund.getAmountValue());
    payload.put("currency", refund.getAmountCurrency());
    payload.put("vendor", refund.getVendor());
    payload.put("reason", request.reason());
    payload.put("eventType", WebhookEventType.PAYMENT_REFUNDED.name());

    webhookOutboxRepository.save(
        WebhookOutbox.builder()
            .transactionId(refund.getId())
            .businessId(refund.getBusinessId())
            .eventType(WebhookEventType.PAYMENT_REFUNDED)
            .payload(payload)
            .status(WebhookOutboxStatus.PENDING)
            .attemptCount(0)
            .nextAttemptAt(Instant.now())
            .build());
  }

  private static String traceId() {
    String value = MDC.get("traceId");
    return value == null ? "-" : value;
  }
}
