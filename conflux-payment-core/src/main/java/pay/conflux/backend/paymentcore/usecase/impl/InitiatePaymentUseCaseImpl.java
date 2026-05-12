package pay.conflux.backend.paymentcore.usecase.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.adapters.port.PaymentProvider;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;
import pay.conflux.backend.adapters.port.VendorPaymentRequest;
import pay.conflux.backend.adapters.port.VendorResponse;
import pay.conflux.backend.adapters.support.PaymentProviderRegistry;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ErrorCode;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.common.security.SecurityUtils;
import pay.conflux.backend.paymentcore.entity.IdempotencyRecord;
import pay.conflux.backend.paymentcore.entity.IdempotencyRecordId;
import pay.conflux.backend.paymentcore.entity.Transaction;
import pay.conflux.backend.paymentcore.entity.TransactionMode;
import pay.conflux.backend.paymentcore.entity.TransactionStatus;
import pay.conflux.backend.paymentcore.entity.WebhookEventType;
import pay.conflux.backend.paymentcore.entity.WebhookOutbox;
import pay.conflux.backend.paymentcore.entity.WebhookOutboxStatus;
import pay.conflux.backend.paymentcore.error.RiskRejectedException;
import pay.conflux.backend.paymentcore.events.PaymentCompletedEvent;
import pay.conflux.backend.paymentcore.events.PaymentFailedEvent;
import pay.conflux.backend.paymentcore.events.PaymentInitiatedEvent;
import pay.conflux.backend.paymentcore.repository.IdempotencyRecordRepository;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.repository.WebhookOutboxRepository;
import pay.conflux.backend.paymentcore.usecase.InitiatePaymentRequest;
import pay.conflux.backend.paymentcore.usecase.InitiatePaymentUseCase;
import pay.conflux.backend.paymentcore.usecase.PaymentInitiationResult;
import pay.conflux.backend.provisioning.usecase.CredentialsResolver;
import pay.conflux.backend.provisioning.usecase.GetVendorConfigUseCase;
import pay.conflux.backend.provisioning.usecase.VendorConfigDescriptor;
import pay.conflux.backend.quota.usecase.ConfirmQuotaUseCase;
import pay.conflux.backend.quota.usecase.QuotaReservation;
import pay.conflux.backend.quota.usecase.ReleaseQuotaUseCase;
import pay.conflux.backend.quota.usecase.ReserveQuotaUseCase;
import pay.conflux.backend.risk.usecase.EvaluateTransactionUseCase;
import pay.conflux.backend.risk.usecase.RiskDecision;
import pay.conflux.backend.risk.usecase.TransactionContext;

/**
 * Orchestrator for {@code POST /api/v1/payments}.
 *
 * <p>Order — every step is binding and verified by unit tests:
 *
 * <ol>
 *   <li>Idempotency check (Redis L1, DB L2). Hit returns the cached response, nothing else runs.
 *   <li>Provisioning lookup ({@link GetVendorConfigUseCase}) — yields {@code (vendor, mode)}.
 *       {@code merchantId} is read from the filter-populated {@code SecurityContext}.
 *   <li>Risk evaluation ({@link EvaluateTransactionUseCase}) — fail-CLOSED. {@code BLOCK} or any
 *       thrown exception aborts with {@link RiskRejectedException}; {@code FLAG} parks the
 *       transaction in {@link TransactionStatus#PENDING_RISK} and does not dispatch.
 *   <li>Quota reservation ({@link ReserveQuotaUseCase}) — PARTNER mode only. Fail-OPEN: any runtime
 *       exception is swallowed and treated as {@link QuotaReservation.Status#FREE}.
 *   <li>Persist {@link Transaction} with {@link TransactionStatus#INITIATED}. After this point the
 *       row stays in the DB; its terminal status reflects what happened.
 *   <li>Adapter dispatch via {@link PaymentProviderRegistry}.
 *   <li>State transition based on the {@link VendorResponse}; on adapter exception the row lands in
 *       {@link TransactionStatus#PENDING_RECOVERY} and the quota reservation is left to the
 *       reconciliation poller (8b).
 *   <li>Publish {@code PaymentInitiatedEvent}.
 *   <li>Enqueue {@link WebhookOutbox} row ({@code PAYMENT_INITIATED}).
 *   <li>Persist {@link IdempotencyRecord} (DB) and write the L1 Redis entry. TTL 24h.
 * </ol>
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class InitiatePaymentUseCaseImpl implements InitiatePaymentUseCase {

  static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
  static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";

  private final TransactionRepository transactionRepository;
  private final WebhookOutboxRepository webhookOutboxRepository;
  private final IdempotencyRecordRepository idempotencyRecordRepository;
  private final GetVendorConfigUseCase getVendorConfigUseCase;
  private final CredentialsResolver credentialsResolver;
  private final EvaluateTransactionUseCase evaluateTransactionUseCase;
  private final ReserveQuotaUseCase reserveQuotaUseCase;
  private final ConfirmQuotaUseCase confirmQuotaUseCase;
  private final ReleaseQuotaUseCase releaseQuotaUseCase;
  private final PaymentProviderRegistry paymentProviderRegistry;
  private final ApplicationEventPublisher eventPublisher;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  public PaymentInitiationResult execute(InitiatePaymentRequest request) {
    // 1. Idempotency check — never log the key itself.
    PaymentInitiationResult cached =
        lookupIdempotent(request.businessId(), request.idempotencyKey());
    if (cached != null) {
      log.debug(
          "Idempotency replay [businessId={}, transactionId={}]",
          request.businessId(),
          cached.transactionId());
      return cached;
    }

    // 2. Provisioning lookup — get vendor config; resolve merchantId from the
    // filter-populated SecurityContext (the gateway API-key filter wrote it there alongside
    // X-Business-Id). Calling GetBusinessByApiKeyUseCase here would re-do that lookup —
    // explicitly forbidden by the 8a contract.
    VendorConfigDescriptor descriptor =
        getVendorConfigUseCase.execute(request.businessId(), request.vendor());
    TransactionMode mode = parseMode(descriptor.mode());
    UUID merchantId = resolveMerchantId(request);

    // 3. Risk evaluation — fail-CLOSED.
    RiskDecision decision = evaluateRiskFailClosed(request, merchantId);
    if (decision.action() == RiskDecision.Action.BLOCK) {
      throw new RiskRejectedException("Transaction blocked by risk engine: " + decision.reason());
    }
    if (decision.action() == RiskDecision.Action.FLAG) {
      // Park in PENDING_RISK; never dispatch. Webhook + idempotency are still cached so the
      // merchant gets a deterministic replay.
      Transaction parked =
          persistTransaction(request, merchantId, mode, TransactionStatus.PENDING_RISK);
      PaymentInitiationResult flagged =
          new PaymentInitiationResult(parked.getId(), "", TransactionStatus.PENDING_RISK.name());
      finishOrchestration(request, parked, flagged, mode, merchantId, null);
      return flagged;
    }

    // 4. Quota reservation — PARTNER mode only, fail-OPEN.
    QuotaReservation reservation =
        mode == TransactionMode.PARTNER ? reserveQuota(merchantId) : null;

    // 5. Persist Transaction (INITIATED).
    Transaction transaction =
        persistTransaction(request, merchantId, mode, TransactionStatus.INITIATED);

    // 6 + 7. Adapter dispatch and state transition.
    PaymentInitiationResult result = dispatchAndTransition(transaction, request, reservation, mode);

    finishOrchestration(request, transaction, result, mode, merchantId, reservation);
    return result;
  }

  // ---------------------------------------------------------------------
  // Step 1 — idempotency
  // ---------------------------------------------------------------------

  private PaymentInitiationResult lookupIdempotent(UUID businessId, String requestKey) {
    String redisKey = redisIdempotencyKey(businessId, requestKey);
    try {
      String raw = redisTemplate.opsForValue().get(redisKey);
      if (raw != null) {
        return deserializeResult(raw);
      }
    } catch (RuntimeException e) {
      log.warn("Redis idempotency L1 lookup failed [businessId={}]", businessId, e);
    }
    Optional<IdempotencyRecord> existing =
        idempotencyRecordRepository.findByBusinessIdAndRequestKey(businessId, requestKey);
    if (existing.isEmpty()) {
      return null;
    }
    IdempotencyRecord record = existing.get();
    if (record.getExpiresAt().isBefore(Instant.now())) {
      return null;
    }
    return mapToResult(record.getResponsePayload());
  }

  // ---------------------------------------------------------------------
  // Step 3 — risk (fail-CLOSED)
  // ---------------------------------------------------------------------

  private RiskDecision evaluateRiskFailClosed(InitiatePaymentRequest request, UUID merchantId) {
    try {
      TransactionContext ctx =
          new TransactionContext(
              merchantId,
              request.amount(),
              request.vendor(),
              metadataValue(request.metadata(), "customer_phone", "+0"),
              metadataValue(request.metadata(), "customer_email", "unknown@conflux.local"),
              metadataValue(request.metadata(), "ip", "0.0.0.0"),
              request.metadata());
      return evaluateTransactionUseCase.execute(ctx);
    } catch (RuntimeException e) {
      log.error(
          "Risk evaluation threw — fail-CLOSED [businessId={}, traceId={}]",
          request.businessId(),
          traceId(),
          e);
      throw new RiskRejectedException("Risk evaluation failed; transaction blocked (fail-CLOSED)");
    }
  }

  // ---------------------------------------------------------------------
  // Step 4 — quota (fail-OPEN)
  // ---------------------------------------------------------------------

  private QuotaReservation reserveQuota(UUID merchantId) {
    try {
      return reserveQuotaUseCase.execute(merchantId);
    } catch (RuntimeException e) {
      log.warn(
          "Quota reservation threw — fail-OPEN, treating as FREE [merchantId={}]", merchantId, e);
      return null;
    }
  }

  // ---------------------------------------------------------------------
  // Step 5 — persist
  // ---------------------------------------------------------------------

  private Transaction persistTransaction(
      InitiatePaymentRequest request,
      UUID merchantId,
      TransactionMode mode,
      TransactionStatus status) {
    Transaction transaction =
        Transaction.builder()
            .businessId(request.businessId())
            .merchantId(merchantId)
            .amountValue(request.amount().amount())
            .amountCurrency(request.amount().currency())
            .status(status)
            .vendor(request.vendor().toUpperCase())
            .mode(mode)
            .merchantOrderReference(request.merchantOrderReference())
            .callbackUrl(request.callbackUrl())
            .webhookUrl(request.webhookUrl())
            .metadata(request.metadata().isEmpty() ? null : new HashMap<>(request.metadata()))
            .retryCount(0)
            .build();
    return transactionRepository.save(transaction);
  }

  // ---------------------------------------------------------------------
  // Steps 6 + 7 — adapter dispatch + state transition
  // ---------------------------------------------------------------------

  private PaymentInitiationResult dispatchAndTransition(
      Transaction transaction,
      InitiatePaymentRequest request,
      QuotaReservation reservation,
      TransactionMode mode) {
    Vendor vendorEnum;
    try {
      vendorEnum = Vendor.valueOf(transaction.getVendor());
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Unsupported vendor: " + transaction.getVendor());
    }

    VendorResponse response;
    try {
      PaymentProvider provider = paymentProviderRegistry.lookup(vendorEnum);
      VendorCredentials creds =
          new VendorCredentials(
              credentialsResolver.resolveCredentials(
                  request.businessId(), transaction.getVendor()));
      VendorPaymentRequest vendorRequest =
          new VendorPaymentRequest(
              transaction.getId(),
              request.amount(),
              request.merchantOrderReference(),
              request.callbackUrl(),
              request.metadata());
      response = provider.initiate(vendorRequest, creds);
    } catch (RuntimeException e) {
      log.warn(
          "Adapter dispatch threw — transitioning to PENDING_RECOVERY"
              + " [transactionId={}, vendor={}, traceId={}]",
          transaction.getId(),
          transaction.getVendor(),
          traceId(),
          e);
      transaction.setStatus(TransactionStatus.PENDING_RECOVERY);
      transactionRepository.save(transaction);
      return new PaymentInitiationResult(
          transaction.getId(), "", TransactionStatus.PENDING_RECOVERY.name());
    }

    return applyVendorResponse(transaction, response, reservation, mode);
  }

  private PaymentInitiationResult applyVendorResponse(
      Transaction transaction,
      VendorResponse response,
      QuotaReservation reservation,
      TransactionMode mode) {
    switch (response.status()) {
      case INITIATED, PENDING -> {
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setVendorTransactionId(response.vendorTrxId());
        transactionRepository.save(transaction);
        return new PaymentInitiationResult(
            transaction.getId(),
            response.redirectUrl() == null ? "" : response.redirectUrl(),
            TransactionStatus.PENDING.name());
      }
      case COMPLETED -> {
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setVendorTransactionId(response.vendorTrxId());
        transactionRepository.save(transaction);
        if (reservation != null) {
          confirmQuotaUseCase.execute(transaction.getMerchantId(), reservation.reservationId());
        }
        // Ledger journal entry is recorded by ledger's PaymentCompletedEvent listener
        // (allowedDependencies forbids payment-core → ledger).
        eventPublisher.publishEvent(buildCompletedEvent(transaction, response, mode));
        return new PaymentInitiationResult(
            transaction.getId(),
            response.redirectUrl() == null ? "" : response.redirectUrl(),
            TransactionStatus.COMPLETED.name());
      }
      case FAILED -> {
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setVendorTransactionId(response.vendorTrxId());
        transactionRepository.save(transaction);
        if (reservation != null) {
          releaseQuotaUseCase.execute(transaction.getMerchantId(), reservation.reservationId());
        }
        eventPublisher.publishEvent(buildFailedEvent(transaction, response));
        return new PaymentInitiationResult(
            transaction.getId(), "", TransactionStatus.FAILED.name());
      }
      case CANCELLED -> {
        transaction.setStatus(TransactionStatus.CANCELLED);
        transaction.setVendorTransactionId(response.vendorTrxId());
        transactionRepository.save(transaction);
        if (reservation != null) {
          releaseQuotaUseCase.execute(transaction.getMerchantId(), reservation.reservationId());
        }
        return new PaymentInitiationResult(
            transaction.getId(), "", TransactionStatus.CANCELLED.name());
      }
      case UNKNOWN -> {
        transaction.setStatus(TransactionStatus.PENDING_RECOVERY);
        transaction.setVendorTransactionId(response.vendorTrxId());
        transactionRepository.save(transaction);
        return new PaymentInitiationResult(
            transaction.getId(), "", TransactionStatus.PENDING_RECOVERY.name());
      }
      default -> throw new IllegalStateException("Unhandled vendor status: " + response.status());
    }
  }

  // ---------------------------------------------------------------------
  // Steps 8–10 — publish event, enqueue webhook, cache idempotency
  // ---------------------------------------------------------------------

  private void finishOrchestration(
      InitiatePaymentRequest request,
      Transaction transaction,
      PaymentInitiationResult result,
      TransactionMode mode,
      UUID merchantId,
      QuotaReservation reservation) {
    eventPublisher.publishEvent(
        new PaymentInitiatedEvent(
            transaction.getId(),
            merchantId,
            transaction.getBusinessId(),
            request.amount(),
            transaction.getVendor(),
            mode.name(),
            transaction.getMerchantOrderReference(),
            request.metadata(),
            Instant.now(),
            traceId()));

    enqueueWebhook(transaction, request);
    persistIdempotency(transaction.getBusinessId(), request.idempotencyKey(), result);
    // Suppress unused-parameter warning while keeping the signature aligned with the orchestration
    // contract; the reservation handle is owned by 6/7 above and is not needed after this point.
    if (reservation != null && log.isTraceEnabled()) {
      log.trace("Reservation settled inline [reservationId={}]", reservation.reservationId());
    }
  }

  private void enqueueWebhook(Transaction transaction, InitiatePaymentRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("transactionId", transaction.getId().toString());
    payload.put("status", transaction.getStatus().name());
    payload.put("amount", transaction.getAmountValue());
    payload.put("currency", transaction.getAmountCurrency());
    payload.put("vendor", transaction.getVendor());
    payload.put("merchantOrderReference", transaction.getMerchantOrderReference());
    payload.put("metadata", request.metadata());

    WebhookOutbox row =
        WebhookOutbox.builder()
            .transactionId(transaction.getId())
            .businessId(transaction.getBusinessId())
            .eventType(WebhookEventType.PAYMENT_INITIATED)
            .payload(payload)
            .status(WebhookOutboxStatus.PENDING)
            .attemptCount(0)
            .nextAttemptAt(Instant.now())
            .build();
    webhookOutboxRepository.save(row);
  }

  private void persistIdempotency(UUID businessId, String requestKey, PaymentInitiationResult r) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("transactionId", r.transactionId().toString());
    payload.put("redirectUrl", r.redirectUrl());
    payload.put("status", r.status());
    IdempotencyRecord record =
        IdempotencyRecord.builder()
            .id(new IdempotencyRecordId(businessId, requestKey))
            .responsePayload(payload)
            .transactionId(r.transactionId())
            .expiresAt(Instant.now().plus(IDEMPOTENCY_TTL))
            .build();
    idempotencyRecordRepository.save(record);

    try {
      redisTemplate
          .opsForValue()
          .set(redisIdempotencyKey(businessId, requestKey), serializeResult(r), IDEMPOTENCY_TTL);
    } catch (RuntimeException e) {
      log.warn("Redis idempotency L1 write failed [businessId={}]", businessId, e);
    }
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private PaymentCompletedEvent buildCompletedEvent(
      Transaction transaction, VendorResponse response, TransactionMode mode) {
    return new PaymentCompletedEvent(
        transaction.getId(),
        transaction.getMerchantId(),
        transaction.getBusinessId(),
        new pay.conflux.backend.common.money.Money(
            transaction.getAmountValue(), transaction.getAmountCurrency()),
        transaction.getVendor(),
        mode.name(),
        transaction.getMerchantOrderReference(),
        transaction.getMetadata() == null ? Map.of() : copyToStringMap(transaction.getMetadata()),
        Instant.now(),
        traceId(),
        response.vendorTrxId() == null ? "" : response.vendorTrxId(),
        pay.conflux.backend.common.money.Money.zero(transaction.getAmountCurrency()));
  }

  private PaymentFailedEvent buildFailedEvent(Transaction transaction, VendorResponse response) {
    return new PaymentFailedEvent(
        transaction.getId(),
        transaction.getMerchantId(),
        transaction.getBusinessId(),
        transaction.getVendor(),
        response.errorCode() == null ? ErrorCode.MFS_ADAPTER_FAILURE : response.errorCode(),
        response.rawResponse() == null ? "vendor returned FAILED" : "vendor returned FAILED",
        transaction.getMetadata() == null ? Map.of() : copyToStringMap(transaction.getMetadata()),
        Instant.now(),
        traceId());
  }

  private static Map<String, String> copyToStringMap(Map<String, String> source) {
    return Map.copyOf(source);
  }

  private TransactionMode parseMode(String mode) {
    try {
      return TransactionMode.valueOf(mode);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Unknown vendor mode: " + mode);
    }
  }

  private UUID resolveMerchantId(InitiatePaymentRequest request) {
    // Prefer the filter-populated SecurityContext (the production path; the 8c API-key filter
    // writes a SecurityUtils-readable AuthenticatedPrincipal). Fall back to a metadata hint so
    // internal callers (e.g. invoice → payment-core) can supply the value without going through
    // the HTTP filter chain.
    return SecurityUtils.currentMerchantId()
        .or(
            () -> {
              String hint =
                  request.metadata() == null ? null : request.metadata().get("merchant_id");
              try {
                return hint == null
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(UUID.fromString(hint));
              } catch (IllegalArgumentException e) {
                return java.util.Optional.empty();
              }
            })
        .orElseThrow(
            () ->
                new UnauthorizedException(
                    "merchantId not present on the request (filter wiring missing?)"));
  }

  private String redisIdempotencyKey(UUID businessId, String requestKey) {
    return IDEMPOTENCY_KEY_PREFIX + businessId + ":" + requestKey;
  }

  private String serializeResult(PaymentInitiationResult result) {
    try {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("transactionId", result.transactionId().toString());
      map.put("redirectUrl", result.redirectUrl());
      map.put("status", result.status());
      return objectMapper.writeValueAsString(map);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize idempotency payload", e);
    }
  }

  private PaymentInitiationResult deserializeResult(String json) {
    try {
      Map<String, Object> map =
          objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
      return mapToResult(map);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deserialize idempotency payload", e);
    }
  }

  private PaymentInitiationResult mapToResult(Map<String, Object> map) {
    UUID id = UUID.fromString(map.get("transactionId").toString());
    String redirectUrl = map.get("redirectUrl") == null ? "" : map.get("redirectUrl").toString();
    String status = map.get("status").toString();
    return new PaymentInitiationResult(id, redirectUrl, status);
  }

  private static String metadataValue(Map<String, String> metadata, String key, String fallback) {
    if (metadata == null) {
      return fallback;
    }
    String value = metadata.get(key);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String traceId() {
    String value = MDC.get("traceId");
    return value == null ? "-" : value;
  }
}
