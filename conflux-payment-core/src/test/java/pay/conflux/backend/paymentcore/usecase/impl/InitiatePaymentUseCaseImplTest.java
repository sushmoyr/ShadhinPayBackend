package pay.conflux.backend.paymentcore.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pay.conflux.backend.adapters.port.PaymentProvider;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;
import pay.conflux.backend.adapters.port.VendorPaymentRequest;
import pay.conflux.backend.adapters.port.VendorResponse;
import pay.conflux.backend.adapters.port.VendorStatus;
import pay.conflux.backend.adapters.support.PaymentProviderRegistry;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.common.security.AuthenticatedPrincipal;
import pay.conflux.backend.paymentcore.entity.Transaction;
import pay.conflux.backend.paymentcore.entity.TransactionStatus;
import pay.conflux.backend.paymentcore.entity.WebhookOutbox;
import pay.conflux.backend.paymentcore.error.RiskRejectedException;
import pay.conflux.backend.paymentcore.events.PaymentCompletedEvent;
import pay.conflux.backend.paymentcore.events.PaymentFailedEvent;
import pay.conflux.backend.paymentcore.events.PaymentInitiatedEvent;
import pay.conflux.backend.paymentcore.repository.IdempotencyRecordRepository;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.repository.WebhookOutboxRepository;
import pay.conflux.backend.paymentcore.usecase.InitiatePaymentRequest;
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

@ExtendWith(MockitoExtension.class)
class InitiatePaymentUseCaseImplTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private WebhookOutboxRepository webhookOutboxRepository;
  @Mock private IdempotencyRecordRepository idempotencyRecordRepository;
  @Mock private GetVendorConfigUseCase getVendorConfigUseCase;
  @Mock private CredentialsResolver credentialsResolver;
  @Mock private EvaluateTransactionUseCase evaluateTransactionUseCase;
  @Mock private ReserveQuotaUseCase reserveQuotaUseCase;
  @Mock private ConfirmQuotaUseCase confirmQuotaUseCase;
  @Mock private ReleaseQuotaUseCase releaseQuotaUseCase;
  @Mock private PaymentProviderRegistry paymentProviderRegistry;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private InitiatePaymentUseCaseImpl useCase;

  private UUID businessId;
  private UUID merchantId;
  private UUID transactionId;

  @BeforeEach
  void setUp() {
    businessId = UUID.randomUUID();
    merchantId = UUID.randomUUID();
    transactionId = UUID.randomUUID();
    useCase =
        new InitiatePaymentUseCaseImpl(
            transactionRepository,
            webhookOutboxRepository,
            idempotencyRecordRepository,
            getVendorConfigUseCase,
            credentialsResolver,
            evaluateTransactionUseCase,
            reserveQuotaUseCase,
            confirmQuotaUseCase,
            releaseQuotaUseCase,
            paymentProviderRegistry,
            eventPublisher,
            redisTemplate,
            objectMapper);
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    lenient().when(valueOperations.get(anyString())).thenReturn(null);

    AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal(
            merchantId,
            AuthenticatedPrincipal.UserType.MERCHANT,
            merchantId,
            businessId,
            AuthenticatedPrincipal.Environment.TEST);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private InitiatePaymentRequest request() {
    return new InitiatePaymentRequest(
        businessId,
        new Money(new BigDecimal("100.00"), "BDT"),
        "MOCK",
        "order-1",
        "https://merchant/return",
        "https://merchant/webhook",
        Map.of(),
        "idem-key-1");
  }

  private void stubProvisioningPartner() {
    when(getVendorConfigUseCase.execute(businessId, "MOCK"))
        .thenReturn(new VendorConfigDescriptor("MOCK", "PARTNER", Map.of()));
  }

  private void stubProvisioningCustom() {
    when(getVendorConfigUseCase.execute(businessId, "MOCK"))
        .thenReturn(new VendorConfigDescriptor("MOCK", "CUSTOM", Map.of("ref", "x")));
  }

  private void stubRiskAllow() {
    when(evaluateTransactionUseCase.execute(any()))
        .thenReturn(new RiskDecision(RiskDecision.Action.ALLOW, 0, List.of(), "ok"));
  }

  private void stubAdapter(VendorResponse response) {
    PaymentProvider provider = mock(PaymentProvider.class);
    when(paymentProviderRegistry.lookup(Vendor.MOCK)).thenReturn(provider);
    when(credentialsResolver.resolveCredentials(businessId, "MOCK")).thenReturn(Map.of("k", "v"));
    when(provider.initiate(any(VendorPaymentRequest.class), any(VendorCredentials.class)))
        .thenReturn(response);
  }

  private void stubSaveAssignsId() {
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            invocation -> {
              Transaction tx = invocation.getArgument(0);
              if (tx.getId() == null) {
                tx.setId(transactionId);
              }
              return tx;
            });
  }

  @Test
  void execute_idempotencyHit_returnsCachedAndSkipsDownstream() {
    String cachedJson =
        "{\"transactionId\":\""
            + transactionId
            + "\",\"redirectUrl\":\"https://r\",\"status\":\"PENDING\"}";
    when(valueOperations.get(anyString())).thenReturn(cachedJson);

    PaymentInitiationResult result = useCase.execute(request());

    assertThat(result.transactionId()).isEqualTo(transactionId);
    assertThat(result.status()).isEqualTo("PENDING");
    verifyNoInteractions(
        getVendorConfigUseCase,
        evaluateTransactionUseCase,
        reserveQuotaUseCase,
        paymentProviderRegistry,
        transactionRepository,
        webhookOutboxRepository,
        eventPublisher);
  }

  @Test
  void execute_riskBlock_throwsAndPersistsNothing() {
    stubProvisioningPartner();
    when(evaluateTransactionUseCase.execute(any()))
        .thenReturn(new RiskDecision(RiskDecision.Action.BLOCK, 100, List.of(), "rule-1"));

    assertThatThrownBy(() -> useCase.execute(request())).isInstanceOf(RiskRejectedException.class);

    verify(transactionRepository, never()).save(any());
    verify(webhookOutboxRepository, never()).save(any());
    verify(idempotencyRecordRepository, never()).save(any());
    verifyNoInteractions(paymentProviderRegistry);
  }

  @Test
  void execute_riskFlag_parksInPendingRiskAndSkipsAdapter() {
    stubProvisioningPartner();
    when(evaluateTransactionUseCase.execute(any()))
        .thenReturn(new RiskDecision(RiskDecision.Action.FLAG, 70, List.of(), "rule-2"));
    stubSaveAssignsId();

    PaymentInitiationResult result = useCase.execute(request());

    assertThat(result.status()).isEqualTo("PENDING_RISK");
    assertThat(result.redirectUrl()).isEmpty();
    ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository, atLeastOnce()).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.PENDING_RISK);
    verify(paymentProviderRegistry, never()).lookup(any());
    verify(webhookOutboxRepository).save(any(WebhookOutbox.class));
    verify(idempotencyRecordRepository).save(any());
  }

  @Test
  void execute_riskThrows_failsClosedNoPersist() {
    stubProvisioningPartner();
    when(evaluateTransactionUseCase.execute(any()))
        .thenThrow(new RuntimeException("risk engine down"));

    assertThatThrownBy(() -> useCase.execute(request())).isInstanceOf(RiskRejectedException.class);

    verify(transactionRepository, never()).save(any());
  }

  @Test
  void execute_quotaThrows_failsOpenAndProceeds() {
    stubProvisioningPartner();
    stubRiskAllow();
    when(reserveQuotaUseCase.execute(merchantId)).thenThrow(new RuntimeException("redis down"));
    stubAdapter(new VendorResponse(VendorStatus.INITIATED, "vendor-x", "https://r", null, null));
    stubSaveAssignsId();

    PaymentInitiationResult result = useCase.execute(request());

    assertThat(result.status()).isEqualTo("PENDING");
    verify(transactionRepository, atLeastOnce()).save(any(Transaction.class));
  }

  @Test
  void execute_partnerMode_reservesQuota() {
    stubProvisioningPartner();
    stubRiskAllow();
    UUID reservationId = UUID.randomUUID();
    when(reserveQuotaUseCase.execute(merchantId))
        .thenReturn(new QuotaReservation(reservationId, QuotaReservation.Status.FREE));
    stubAdapter(new VendorResponse(VendorStatus.INITIATED, "vendor-x", "https://r", null, null));
    stubSaveAssignsId();

    useCase.execute(request());

    verify(reserveQuotaUseCase).execute(merchantId);
  }

  @Test
  void execute_customMode_skipsQuota() {
    stubProvisioningCustom();
    stubRiskAllow();
    stubAdapter(new VendorResponse(VendorStatus.INITIATED, "vendor-x", "https://r", null, null));
    stubSaveAssignsId();

    useCase.execute(request());

    verifyNoInteractions(reserveQuotaUseCase, confirmQuotaUseCase, releaseQuotaUseCase);
  }

  @Test
  void execute_adapterInitiated_setsPendingAndStoresVendorTrxId() {
    stubProvisioningPartner();
    stubRiskAllow();
    when(reserveQuotaUseCase.execute(merchantId))
        .thenReturn(new QuotaReservation(UUID.randomUUID(), QuotaReservation.Status.FREE));
    stubAdapter(
        new VendorResponse(VendorStatus.INITIATED, "vendor-trx-1", "https://r", null, null));
    stubSaveAssignsId();

    PaymentInitiationResult result = useCase.execute(request());

    assertThat(result.status()).isEqualTo("PENDING");
    ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository, atLeastOnce()).save(captor.capture());
    Transaction last = captor.getAllValues().get(captor.getAllValues().size() - 1);
    assertThat(last.getStatus()).isEqualTo(TransactionStatus.PENDING);
    assertThat(last.getVendorTransactionId()).isEqualTo("vendor-trx-1");
  }

  @Test
  void execute_adapterCompleted_confirmsQuotaAndPublishesCompletedEvent() {
    stubProvisioningPartner();
    stubRiskAllow();
    UUID reservationId = UUID.randomUUID();
    when(reserveQuotaUseCase.execute(merchantId))
        .thenReturn(new QuotaReservation(reservationId, QuotaReservation.Status.FREE));
    stubAdapter(new VendorResponse(VendorStatus.COMPLETED, "vt-2", null, null, null));
    stubSaveAssignsId();

    PaymentInitiationResult result = useCase.execute(request());

    assertThat(result.status()).isEqualTo("COMPLETED");
    verify(confirmQuotaUseCase).execute(merchantId, reservationId);
    verify(eventPublisher).publishEvent(any(PaymentCompletedEvent.class));
    verify(eventPublisher).publishEvent(any(PaymentInitiatedEvent.class));
  }

  @Test
  void execute_adapterFailed_releasesQuotaAndPublishesFailedEvent() {
    stubProvisioningPartner();
    stubRiskAllow();
    UUID reservationId = UUID.randomUUID();
    when(reserveQuotaUseCase.execute(merchantId))
        .thenReturn(new QuotaReservation(reservationId, QuotaReservation.Status.FREE));
    stubAdapter(
        new VendorResponse(
            VendorStatus.FAILED,
            "vt-3",
            null,
            "vendor decline",
            pay.conflux.backend.common.error.ErrorCode.MFS_ADAPTER_FAILURE));
    stubSaveAssignsId();

    PaymentInitiationResult result = useCase.execute(request());

    assertThat(result.status()).isEqualTo("FAILED");
    verify(releaseQuotaUseCase).execute(merchantId, reservationId);
    verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
  }

  @Test
  void execute_adapterThrows_marksPendingRecoveryAndKeepsReservation() {
    stubProvisioningPartner();
    stubRiskAllow();
    UUID reservationId = UUID.randomUUID();
    when(reserveQuotaUseCase.execute(merchantId))
        .thenReturn(new QuotaReservation(reservationId, QuotaReservation.Status.FREE));
    PaymentProvider provider = mock(PaymentProvider.class);
    when(paymentProviderRegistry.lookup(Vendor.MOCK)).thenReturn(provider);
    when(credentialsResolver.resolveCredentials(businessId, "MOCK")).thenReturn(Map.of("k", "v"));
    when(provider.initiate(any(), any())).thenThrow(new RuntimeException("vendor timeout"));
    stubSaveAssignsId();

    PaymentInitiationResult result = useCase.execute(request());

    assertThat(result.status()).isEqualTo("PENDING_RECOVERY");
    verify(releaseQuotaUseCase, never()).execute(eq(merchantId), any());
    verify(confirmQuotaUseCase, never()).execute(eq(merchantId), any());
  }

  @Test
  void execute_adapterCancelled_landsCancelledAndReleasesQuota() {
    stubProvisioningPartner();
    stubRiskAllow();
    UUID reservationId = UUID.randomUUID();
    when(reserveQuotaUseCase.execute(merchantId))
        .thenReturn(new QuotaReservation(reservationId, QuotaReservation.Status.FREE));
    stubAdapter(new VendorResponse(VendorStatus.CANCELLED, "vt-c", null, null, null));
    stubSaveAssignsId();

    PaymentInitiationResult result = useCase.execute(request());

    assertThat(result.status()).isEqualTo("CANCELLED");
    verify(releaseQuotaUseCase).execute(merchantId, reservationId);
    verify(confirmQuotaUseCase, never()).execute(any(), any());
  }

  @Test
  void execute_adapterUnknown_landsPendingRecoveryAndPreservesReservation() {
    stubProvisioningPartner();
    stubRiskAllow();
    UUID reservationId = UUID.randomUUID();
    when(reserveQuotaUseCase.execute(merchantId))
        .thenReturn(new QuotaReservation(reservationId, QuotaReservation.Status.FREE));
    stubAdapter(new VendorResponse(VendorStatus.UNKNOWN, "vt-u", null, null, null));
    stubSaveAssignsId();

    PaymentInitiationResult result = useCase.execute(request());

    assertThat(result.status()).isEqualTo("PENDING_RECOVERY");
    verify(releaseQuotaUseCase, never()).execute(any(), any());
    verify(confirmQuotaUseCase, never()).execute(any(), any());
  }

  @Test
  void execute_idempotencyRecordInDbButRedisCold_returnsCachedFromDb() {
    String idemKey = "ipk-db-hit";
    UUID existingTxId = UUID.randomUUID();
    java.util.Map<String, Object> payload =
        java.util.Map.of(
            "transactionId",
            existingTxId.toString(),
            "redirectUrl",
            "https://r",
            "status",
            "PENDING");
    pay.conflux.backend.paymentcore.entity.IdempotencyRecord existing =
        pay.conflux.backend.paymentcore.entity.IdempotencyRecord.builder()
            .id(new pay.conflux.backend.paymentcore.entity.IdempotencyRecordId(businessId, idemKey))
            .responsePayload(payload)
            .transactionId(existingTxId)
            .expiresAt(java.time.Instant.now().plusSeconds(60))
            .build();
    when(idempotencyRecordRepository.findByBusinessIdAndRequestKey(businessId, idemKey))
        .thenReturn(java.util.Optional.of(existing));

    PaymentInitiationResult result =
        useCase.execute(
            new pay.conflux.backend.paymentcore.usecase.InitiatePaymentRequest(
                businessId,
                new Money(new BigDecimal("100.00"), "BDT"),
                "MOCK",
                "order-1",
                "https://merchant/return",
                "https://merchant/webhook",
                java.util.Map.of(),
                idemKey));

    assertThat(result.transactionId()).isEqualTo(existingTxId);
    verifyNoInteractions(
        getVendorConfigUseCase,
        evaluateTransactionUseCase,
        paymentProviderRegistry,
        transactionRepository,
        webhookOutboxRepository);
  }

  @Test
  void execute_persistsWebhookOutboxAndIdempotencyRecord() {
    stubProvisioningPartner();
    stubRiskAllow();
    when(reserveQuotaUseCase.execute(merchantId))
        .thenReturn(new QuotaReservation(UUID.randomUUID(), QuotaReservation.Status.FREE));
    stubAdapter(new VendorResponse(VendorStatus.INITIATED, "vt", "https://r", null, null));
    stubSaveAssignsId();

    useCase.execute(request());

    verify(webhookOutboxRepository, times(1)).save(any(WebhookOutbox.class));
    verify(idempotencyRecordRepository, times(1)).save(any());
    verify(valueOperations).set(anyString(), anyString(), any());
  }
}
