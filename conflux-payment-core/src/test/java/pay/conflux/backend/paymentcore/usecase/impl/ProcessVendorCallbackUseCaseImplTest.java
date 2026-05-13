package pay.conflux.backend.paymentcore.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pay.conflux.backend.adapters.bkash.BkashAdapter;
import pay.conflux.backend.adapters.port.PaymentProvider;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;
import pay.conflux.backend.adapters.port.VendorResponse;
import pay.conflux.backend.adapters.port.VendorStatus;
import pay.conflux.backend.adapters.support.PaymentProviderRegistry;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.paymentcore.entity.Transaction;
import pay.conflux.backend.paymentcore.entity.TransactionMode;
import pay.conflux.backend.paymentcore.entity.TransactionStatus;
import pay.conflux.backend.paymentcore.entity.WebhookEventType;
import pay.conflux.backend.paymentcore.entity.WebhookOutbox;
import pay.conflux.backend.paymentcore.events.PaymentCompletedEvent;
import pay.conflux.backend.paymentcore.events.PaymentFailedEvent;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.repository.WebhookOutboxRepository;
import pay.conflux.backend.paymentcore.usecase.ProcessVendorCallbackResult;
import pay.conflux.backend.provisioning.usecase.CredentialsResolver;

@ExtendWith(MockitoExtension.class)
class ProcessVendorCallbackUseCaseImplTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private WebhookOutboxRepository webhookOutboxRepository;
  @Mock private PaymentProviderRegistry paymentProviderRegistry;
  @Mock private CredentialsResolver credentialsResolver;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private PaymentProvider provider;
  @Mock private BkashAdapter bkashAdapter;

  @InjectMocks private ProcessVendorCallbackUseCaseImpl useCase;

  private UUID transactionId;
  private UUID businessId;
  private UUID merchantId;
  private Transaction pendingTx;

  @BeforeEach
  void setUp() {
    transactionId = UUID.randomUUID();
    businessId = UUID.randomUUID();
    merchantId = UUID.randomUUID();
    pendingTx = baseTx(TransactionStatus.PENDING);
  }

  private Transaction baseTx(TransactionStatus status) {
    return Transaction.builder()
        .id(transactionId)
        .businessId(businessId)
        .merchantId(merchantId)
        .amountValue(new BigDecimal("100.00"))
        .amountCurrency("BDT")
        .status(status)
        .vendor("MOCK")
        .mode(TransactionMode.PARTNER)
        .merchantOrderReference("order-1")
        .vendorTransactionId("vendor-trx-1")
        .retryCount(0)
        .metadata(new HashMap<>())
        .build();
  }

  private void stubVendorResponse(VendorStatus status) {
    when(transactionRepository.findByVendorTransactionId("vendor-trx-1"))
        .thenReturn(Optional.of(pendingTx));
    when(paymentProviderRegistry.lookup(Vendor.MOCK)).thenReturn(provider);
    when(credentialsResolver.resolveCredentials(businessId, "MOCK")).thenReturn(Map.of("k", "v"));
    when(provider.queryStatus(eq("vendor-trx-1"), any(VendorCredentials.class)))
        .thenReturn(new VendorResponse(status, "vendor-trx-1", null, null, null));
    when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void execute_vendorCompleted_updatesStatusAndEnqueuesWebhook() {
    stubVendorResponse(VendorStatus.COMPLETED);

    ProcessVendorCallbackResult result =
        useCase.execute("MOCK", Map.of("mock_trx_id", "vendor-trx-1"));

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(pendingTx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    verify(eventPublisher).publishEvent(any(PaymentCompletedEvent.class));
    ArgumentCaptor<WebhookOutbox> captor = ArgumentCaptor.forClass(WebhookOutbox.class);
    verify(webhookOutboxRepository).save(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo(WebhookEventType.PAYMENT_COMPLETED);
  }

  @Test
  void execute_vendorFailed_updatesStatusAndPublishesFailedEvent() {
    stubVendorResponse(VendorStatus.FAILED);

    ProcessVendorCallbackResult result =
        useCase.execute("MOCK", Map.of("mock_trx_id", "vendor-trx-1"));

    assertThat(result.status()).isEqualTo("FAILED");
    assertThat(pendingTx.getStatus()).isEqualTo(TransactionStatus.FAILED);
    verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
    ArgumentCaptor<WebhookOutbox> captor = ArgumentCaptor.forClass(WebhookOutbox.class);
    verify(webhookOutboxRepository).save(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo(WebhookEventType.PAYMENT_FAILED);
  }

  @Test
  void execute_vendorCancelled_enqueuesFailedWebhookWithoutEvent() {
    stubVendorResponse(VendorStatus.CANCELLED);

    ProcessVendorCallbackResult result =
        useCase.execute("MOCK", Map.of("mock_trx_id", "vendor-trx-1"));

    assertThat(result.status()).isEqualTo("CANCELLED");
    assertThat(pendingTx.getStatus()).isEqualTo(TransactionStatus.CANCELLED);
    verify(eventPublisher, never()).publishEvent(any(PaymentCompletedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(PaymentFailedEvent.class));
    verify(webhookOutboxRepository).save(any(WebhookOutbox.class));
  }

  @Test
  void execute_vendorStillPending_noTransitionNoSideEffects() {
    when(transactionRepository.findByVendorTransactionId("vendor-trx-1"))
        .thenReturn(Optional.of(pendingTx));
    when(paymentProviderRegistry.lookup(Vendor.MOCK)).thenReturn(provider);
    when(credentialsResolver.resolveCredentials(businessId, "MOCK")).thenReturn(Map.of("k", "v"));
    when(provider.queryStatus(eq("vendor-trx-1"), any(VendorCredentials.class)))
        .thenReturn(new VendorResponse(VendorStatus.PENDING, "vendor-trx-1", null, null, null));

    ProcessVendorCallbackResult result =
        useCase.execute("MOCK", Map.of("mock_trx_id", "vendor-trx-1"));

    assertThat(result.status()).isEqualTo("PENDING");
    assertThat(pendingTx.getStatus()).isEqualTo(TransactionStatus.PENDING);
    verifyNoInteractions(eventPublisher);
    verifyNoInteractions(webhookOutboxRepository);
    verify(transactionRepository, never()).save(any(Transaction.class));
  }

  @Test
  void execute_queryStatusThrows_landsPendingRecovery() {
    when(transactionRepository.findByVendorTransactionId("vendor-trx-1"))
        .thenReturn(Optional.of(pendingTx));
    when(paymentProviderRegistry.lookup(Vendor.MOCK)).thenReturn(provider);
    when(credentialsResolver.resolveCredentials(businessId, "MOCK")).thenReturn(Map.of("k", "v"));
    when(provider.queryStatus(eq("vendor-trx-1"), any(VendorCredentials.class)))
        .thenThrow(new RuntimeException("vendor timeout"));
    when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

    ProcessVendorCallbackResult result =
        useCase.execute("MOCK", Map.of("mock_trx_id", "vendor-trx-1"));

    assertThat(result.status()).isEqualTo("PENDING_RECOVERY");
    assertThat(pendingTx.getStatus()).isEqualTo(TransactionStatus.PENDING_RECOVERY);
    verifyNoInteractions(eventPublisher);
    verifyNoInteractions(webhookOutboxRepository);
  }

  @Test
  void execute_alreadyCompleted_isNoOp() {
    Transaction completedTx = baseTx(TransactionStatus.COMPLETED);
    when(transactionRepository.findByVendorTransactionId("vendor-trx-1"))
        .thenReturn(Optional.of(completedTx));

    // First call should be a no-op (already terminal). Run twice to make the invariant explicit.
    useCase.execute("MOCK", Map.of("mock_trx_id", "vendor-trx-1"));
    useCase.execute("MOCK", Map.of("mock_trx_id", "vendor-trx-1"));

    verify(eventPublisher, never()).publishEvent(any());
    verify(webhookOutboxRepository, never()).save(any(WebhookOutbox.class));
    verify(transactionRepository, never()).save(any(Transaction.class));
    verifyNoInteractions(paymentProviderRegistry);
  }

  @Test
  void execute_missingVendorParam_throwsValidation() {
    assertThatThrownBy(() -> useCase.execute("MOCK", Map.of()))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void execute_unknownVendorTrxId_throwsResourceNotFound() {
    when(transactionRepository.findByVendorTransactionId("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute("MOCK", Map.of("mock_trx_id", "unknown")))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void execute_vendorMismatch_throwsValidation() {
    when(transactionRepository.findByVendorTransactionId("vendor-trx-1"))
        .thenReturn(Optional.of(pendingTx));

    assertThatThrownBy(() -> useCase.execute("BKASH", Map.of("mock_trx_id", "vendor-trx-1")))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void execute_vendorInitiated_stillPending_noTransition() {
    when(transactionRepository.findByVendorTransactionId("vendor-trx-1"))
        .thenReturn(Optional.of(pendingTx));
    when(paymentProviderRegistry.lookup(Vendor.MOCK)).thenReturn(provider);
    when(credentialsResolver.resolveCredentials(businessId, "MOCK")).thenReturn(Map.of("k", "v"));
    when(provider.queryStatus(eq("vendor-trx-1"), any(VendorCredentials.class)))
        .thenReturn(new VendorResponse(VendorStatus.INITIATED, "vendor-trx-1", null, null, null));

    ProcessVendorCallbackResult result =
        useCase.execute("MOCK", Map.of("mock_trx_id", "vendor-trx-1"));

    assertThat(result.status()).isEqualTo("PENDING");
    verifyNoInteractions(eventPublisher);
    verifyNoInteractions(webhookOutboxRepository);
  }

  @Test
  void execute_vendorUnknown_landsPendingRecovery() {
    when(transactionRepository.findByVendorTransactionId("vendor-trx-1"))
        .thenReturn(Optional.of(pendingTx));
    when(paymentProviderRegistry.lookup(Vendor.MOCK)).thenReturn(provider);
    when(credentialsResolver.resolveCredentials(businessId, "MOCK")).thenReturn(Map.of("k", "v"));
    when(provider.queryStatus(eq("vendor-trx-1"), any(VendorCredentials.class)))
        .thenReturn(new VendorResponse(VendorStatus.UNKNOWN, "vendor-trx-1", null, null, null));
    when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

    ProcessVendorCallbackResult result =
        useCase.execute("MOCK", Map.of("mock_trx_id", "vendor-trx-1"));

    assertThat(result.status()).isEqualTo("PENDING_RECOVERY");
    assertThat(pendingTx.getStatus()).isEqualTo(TransactionStatus.PENDING_RECOVERY);
  }

  @Test
  void execute_blankVendor_throwsValidation() {
    assertThatThrownBy(() -> useCase.execute("", Map.of("mock_trx_id", "x")))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void execute_nullParams_throwsValidation() {
    assertThatThrownBy(() -> useCase.execute("MOCK", null)).isInstanceOf(ValidationException.class);
  }

  @Test
  void resolveByTransactionId_unknownId_throwsResourceNotFound() {
    UUID id = UUID.randomUUID();
    when(transactionRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.resolveByTransactionId(id))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void resolveByTransactionId_transitionsViaQueryStatus() {
    when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(pendingTx));
    when(paymentProviderRegistry.lookup(Vendor.MOCK)).thenReturn(provider);
    when(credentialsResolver.resolveCredentials(businessId, "MOCK")).thenReturn(Map.of("k", "v"));
    when(provider.queryStatus(eq("vendor-trx-1"), any(VendorCredentials.class)))
        .thenReturn(new VendorResponse(VendorStatus.COMPLETED, "vendor-trx-1", null, null, null));
    when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

    ProcessVendorCallbackResult result = useCase.resolveByTransactionId(transactionId);

    assertThat(result.status()).isEqualTo("COMPLETED");
    verify(eventPublisher, times(1)).publishEvent(any(PaymentCompletedEvent.class));
  }

  // ---------------------------------------------------------------------
  // Bkash Execute wiring (Wave D follow-up)
  // ---------------------------------------------------------------------

  private Transaction bkashTx() {
    return Transaction.builder()
        .id(transactionId)
        .businessId(businessId)
        .merchantId(merchantId)
        .amountValue(new BigDecimal("100.00"))
        .amountCurrency("BDT")
        .status(TransactionStatus.PENDING)
        .vendor("BKASH")
        .mode(TransactionMode.PARTNER)
        .merchantOrderReference("order-bkash-1")
        .vendorTransactionId("PMT-BKASH-1")
        .retryCount(0)
        .metadata(new HashMap<>())
        .build();
  }

  @Test
  void execute_bkashCallback_confirmSucceeds_marksCompletedAndPublishesEvent() {
    Transaction tx = bkashTx();
    when(transactionRepository.findByVendorTransactionId("PMT-BKASH-1"))
        .thenReturn(Optional.of(tx));
    when(credentialsResolver.resolveCredentials(businessId, "BKASH"))
        .thenReturn(Map.of("appKey", "x", "baseUrl", "https://sandbox"));
    when(bkashAdapter.confirm(eq("PMT-BKASH-1"), any(VendorCredentials.class)))
        .thenReturn(new VendorResponse(VendorStatus.COMPLETED, "PMT-BKASH-1", null, null, null));
    when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

    ProcessVendorCallbackResult result =
        useCase.execute("BKASH", Map.of("mock_trx_id", "PMT-BKASH-1"));

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    verify(bkashAdapter, times(1)).confirm(eq("PMT-BKASH-1"), any(VendorCredentials.class));
    verifyNoInteractions(paymentProviderRegistry);
    verify(eventPublisher).publishEvent(any(PaymentCompletedEvent.class));
    ArgumentCaptor<WebhookOutbox> captor = ArgumentCaptor.forClass(WebhookOutbox.class);
    verify(webhookOutboxRepository).save(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo(WebhookEventType.PAYMENT_COMPLETED);
  }

  @Test
  void execute_bkashCallback_confirmFails_landsPendingRecoveryNoEvent() {
    Transaction tx = bkashTx();
    when(transactionRepository.findByVendorTransactionId("PMT-BKASH-1"))
        .thenReturn(Optional.of(tx));
    when(credentialsResolver.resolveCredentials(businessId, "BKASH"))
        .thenReturn(Map.of("appKey", "x", "baseUrl", "https://sandbox"));
    when(bkashAdapter.confirm(eq("PMT-BKASH-1"), any(VendorCredentials.class)))
        .thenReturn(
            new VendorResponse(
                VendorStatus.FAILED,
                "PMT-BKASH-1",
                null,
                "{\"statusCode\":\"2056\"}",
                pay.conflux.backend.common.error.ErrorCode.MFS_ADAPTER_FAILURE));
    when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

    ProcessVendorCallbackResult result =
        useCase.execute("BKASH", Map.of("mock_trx_id", "PMT-BKASH-1"));

    assertThat(result.status()).isEqualTo("PENDING_RECOVERY");
    assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING_RECOVERY);
    verify(bkashAdapter, times(1)).confirm(eq("PMT-BKASH-1"), any(VendorCredentials.class));
    verify(eventPublisher, never()).publishEvent(any(PaymentCompletedEvent.class));
    verify(webhookOutboxRepository, never()).save(any(WebhookOutbox.class));
  }

  @Test
  void execute_sslcommerzCallback_doesNotInvokeBkashAdapter() {
    Transaction sslTx =
        Transaction.builder()
            .id(transactionId)
            .businessId(businessId)
            .merchantId(merchantId)
            .amountValue(new BigDecimal("100.00"))
            .amountCurrency("BDT")
            .status(TransactionStatus.PENDING)
            .vendor("SSLCOMMERZ")
            .mode(TransactionMode.PARTNER)
            .merchantOrderReference("order-ssl-1")
            .vendorTransactionId("SSL-TRX-1")
            .retryCount(0)
            .metadata(new HashMap<>())
            .build();
    when(transactionRepository.findByVendorTransactionId("SSL-TRX-1"))
        .thenReturn(Optional.of(sslTx));
    when(paymentProviderRegistry.lookup(Vendor.SSLCOMMERZ)).thenReturn(provider);
    when(credentialsResolver.resolveCredentials(businessId, "SSLCOMMERZ"))
        .thenReturn(Map.of("store_id", "x"));
    when(provider.queryStatus(eq("SSL-TRX-1"), any(VendorCredentials.class)))
        .thenReturn(new VendorResponse(VendorStatus.COMPLETED, "SSL-TRX-1", null, null, null));
    when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

    ProcessVendorCallbackResult result =
        useCase.execute("SSLCOMMERZ", Map.of("mock_trx_id", "SSL-TRX-1"));

    assertThat(result.status()).isEqualTo("COMPLETED");
    verifyNoInteractions(bkashAdapter);
  }
}
