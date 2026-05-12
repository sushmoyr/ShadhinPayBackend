package pay.conflux.backend.paymentcore.usecase.impl;

import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.adapters.port.PaymentProvider;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;
import pay.conflux.backend.adapters.port.VendorResponse;
import pay.conflux.backend.adapters.support.PaymentProviderRegistry;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ErrorCode;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.paymentcore.entity.Transaction;
import pay.conflux.backend.paymentcore.entity.TransactionStatus;
import pay.conflux.backend.paymentcore.entity.WebhookEventType;
import pay.conflux.backend.paymentcore.entity.WebhookOutbox;
import pay.conflux.backend.paymentcore.entity.WebhookOutboxStatus;
import pay.conflux.backend.paymentcore.events.PaymentCompletedEvent;
import pay.conflux.backend.paymentcore.events.PaymentFailedEvent;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.repository.WebhookOutboxRepository;
import pay.conflux.backend.paymentcore.usecase.ProcessVendorCallbackResult;
import pay.conflux.backend.paymentcore.usecase.ProcessVendorCallbackUseCase;
import pay.conflux.backend.provisioning.usecase.CredentialsResolver;

/**
 * Handler for the vendor-return callback. Re-queries the vendor for the authoritative status and
 * transitions the {@code Transaction} accordingly.
 *
 * <p>Idempotent on terminal states — re-running for an already-{@code COMPLETED}/{@code FAILED}/
 * {@code CANCELLED} transaction is a no-op. {@code @Retryable} catches version-column collisions
 * (two callbacks racing the reconciliation poller).
 *
 * <p>The use case never finalizes {@code PENDING_RECOVERY} to {@code FAILED} on a vendor timeout —
 * that path is reserved for the {@code ReconciliationScheduler}'s 24h-timeout case.
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class ProcessVendorCallbackUseCaseImpl implements ProcessVendorCallbackUseCase {

  static final String MOCK_VENDOR_TRX_PARAM = "mock_trx_id";

  private final TransactionRepository transactionRepository;
  private final WebhookOutboxRepository webhookOutboxRepository;
  private final PaymentProviderRegistry paymentProviderRegistry;
  private final CredentialsResolver credentialsResolver;
  private final ApplicationEventPublisher eventPublisher;

  // Quota reservation settlement on the callback path:
  // the reservation handle is not persisted on the Transaction (would require a schema bump
  // beyond 8b's scope). Pending PARTNER reservations are reclaimed by the quota module's own
  // 30-minute leaked-reservation cleanup job — see DOCS/features/quota/TECH_SPEC.md.

  @Override
  @Transactional
  @Retryable(
      retryFor = {OptimisticLockException.class, ObjectOptimisticLockingFailureException.class},
      maxAttempts = 5,
      backoff = @Backoff(delay = 50, multiplier = 2.0, maxDelay = 1000))
  public ProcessVendorCallbackResult execute(String vendor, Map<String, String> callbackParams) {
    Transaction transaction = resolveTransaction(vendor, callbackParams);
    return transitionFromVendor(transaction);
  }

  @Override
  @Transactional
  @Retryable(
      retryFor = {OptimisticLockException.class, ObjectOptimisticLockingFailureException.class},
      maxAttempts = 5,
      backoff = @Backoff(delay = 50, multiplier = 2.0, maxDelay = 1000))
  public ProcessVendorCallbackResult resolveByTransactionId(UUID transactionId) {
    Transaction transaction =
        transactionRepository
            .findById(transactionId)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
    return transitionFromVendor(transaction);
  }

  // ---------------------------------------------------------------------
  // Resolution & state machine
  // ---------------------------------------------------------------------

  private Transaction resolveTransaction(String vendor, Map<String, String> params) {
    if (vendor == null || vendor.isBlank()) {
      throw new ValidationException("vendor path variable is required");
    }
    String vendorTrxId = params == null ? null : params.get(MOCK_VENDOR_TRX_PARAM);
    if (vendorTrxId == null || vendorTrxId.isBlank()) {
      throw new ValidationException(
          "callback is missing the vendor transaction id (" + MOCK_VENDOR_TRX_PARAM + ")");
    }
    Transaction transaction =
        transactionRepository
            .findByVendorTransactionId(vendorTrxId)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction", vendorTrxId));
    if (!transaction.getVendor().equalsIgnoreCase(vendor)) {
      throw new ValidationException(
          "Callback vendor "
              + vendor
              + " does not match the transaction's vendor "
              + transaction.getVendor());
    }
    return transaction;
  }

  private ProcessVendorCallbackResult transitionFromVendor(Transaction transaction) {
    if (isTerminal(transaction.getStatus())) {
      log.debug(
          "Vendor callback for already-terminal transaction — no-op [transactionId={}, status={}]",
          transaction.getId(),
          transaction.getStatus());
      return new ProcessVendorCallbackResult(transaction.getId(), transaction.getStatus().name());
    }

    Vendor vendorEnum;
    try {
      vendorEnum = Vendor.valueOf(transaction.getVendor());
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Unsupported vendor: " + transaction.getVendor(), e);
    }

    VendorResponse response;
    try {
      PaymentProvider provider = paymentProviderRegistry.lookup(vendorEnum);
      VendorCredentials creds =
          new VendorCredentials(
              credentialsResolver.resolveCredentials(
                  transaction.getBusinessId(), transaction.getVendor()));
      response = provider.queryStatus(transaction.getVendorTransactionId(), creds);
    } catch (RuntimeException e) {
      log.warn(
          "queryStatus threw — leaving transaction in PENDING_RECOVERY [transactionId={},"
              + " vendor={}, traceId={}]",
          transaction.getId(),
          transaction.getVendor(),
          traceId(),
          e);
      if (transaction.getStatus() != TransactionStatus.PENDING_RECOVERY) {
        transaction.setStatus(TransactionStatus.PENDING_RECOVERY);
        transactionRepository.save(transaction);
      }
      return new ProcessVendorCallbackResult(
          transaction.getId(), TransactionStatus.PENDING_RECOVERY.name());
    }

    return apply(transaction, response);
  }

  private ProcessVendorCallbackResult apply(Transaction transaction, VendorResponse response) {
    switch (response.status()) {
      case COMPLETED -> {
        transaction.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(transaction);
        eventPublisher.publishEvent(buildCompletedEvent(transaction, response));
        enqueueWebhook(transaction, WebhookEventType.PAYMENT_COMPLETED);
      }
      case FAILED -> {
        transaction.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(transaction);
        eventPublisher.publishEvent(
            buildFailedEvent(transaction, response, "vendor returned FAILED"));
        enqueueWebhook(transaction, WebhookEventType.PAYMENT_FAILED);
      }
      case CANCELLED -> {
        transaction.setStatus(TransactionStatus.CANCELLED);
        transactionRepository.save(transaction);
        enqueueWebhook(transaction, WebhookEventType.PAYMENT_FAILED);
      }
      case INITIATED, PENDING -> {
        log.debug(
            "Vendor reports still-pending state — no transition [transactionId={},"
                + " vendorStatus={}]",
            transaction.getId(),
            response.status());
      }
      case UNKNOWN -> {
        if (transaction.getStatus() != TransactionStatus.PENDING_RECOVERY) {
          transaction.setStatus(TransactionStatus.PENDING_RECOVERY);
          transactionRepository.save(transaction);
        }
      }
      default -> throw new IllegalStateException("Unhandled vendor status: " + response.status());
    }
    return new ProcessVendorCallbackResult(transaction.getId(), transaction.getStatus().name());
  }

  private static boolean isTerminal(TransactionStatus status) {
    return status == TransactionStatus.COMPLETED
        || status == TransactionStatus.FAILED
        || status == TransactionStatus.CANCELLED;
  }

  // ---------------------------------------------------------------------
  // Side effects
  // ---------------------------------------------------------------------

  private void enqueueWebhook(Transaction transaction, WebhookEventType eventType) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("transactionId", transaction.getId().toString());
    payload.put("status", transaction.getStatus().name());
    payload.put("amount", transaction.getAmountValue());
    payload.put("currency", transaction.getAmountCurrency());
    payload.put("vendor", transaction.getVendor());
    payload.put("merchantOrderReference", transaction.getMerchantOrderReference());
    payload.put("eventType", eventType.name());

    WebhookOutbox row =
        WebhookOutbox.builder()
            .transactionId(transaction.getId())
            .businessId(transaction.getBusinessId())
            .eventType(eventType)
            .payload(payload)
            .status(WebhookOutboxStatus.PENDING)
            .attemptCount(0)
            .nextAttemptAt(Instant.now())
            .build();
    webhookOutboxRepository.save(row);
  }

  private PaymentCompletedEvent buildCompletedEvent(Transaction transaction, VendorResponse vr) {
    return new PaymentCompletedEvent(
        transaction.getId(),
        transaction.getMerchantId(),
        transaction.getBusinessId(),
        new Money(transaction.getAmountValue(), transaction.getAmountCurrency()),
        transaction.getVendor(),
        transaction.getMode().name(),
        transaction.getMerchantOrderReference(),
        transaction.getMetadata() == null ? Map.of() : Map.copyOf(transaction.getMetadata()),
        Instant.now(),
        traceId(),
        vr.vendorTrxId() == null
            ? (transaction.getVendorTransactionId() == null
                ? ""
                : transaction.getVendorTransactionId())
            : vr.vendorTrxId(),
        Money.zero(transaction.getAmountCurrency()));
  }

  private PaymentFailedEvent buildFailedEvent(
      Transaction transaction, VendorResponse vr, String reason) {
    return new PaymentFailedEvent(
        transaction.getId(),
        transaction.getMerchantId(),
        transaction.getBusinessId(),
        transaction.getVendor(),
        vr.errorCode() == null ? ErrorCode.MFS_ADAPTER_FAILURE : vr.errorCode(),
        reason,
        transaction.getMetadata() == null ? Map.of() : Map.copyOf(transaction.getMetadata()),
        Instant.now(),
        traceId());
  }

  private static String traceId() {
    String value = MDC.get("traceId");
    return value == null ? "-" : value;
  }
}
