package pay.conflux.backend.paymentcore.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pay.conflux.backend.adapters.port.PaymentProvider;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;
import pay.conflux.backend.adapters.port.VendorRefundRequest;
import pay.conflux.backend.adapters.port.VendorResponse;
import pay.conflux.backend.adapters.port.VendorStatus;
import pay.conflux.backend.adapters.support.PaymentProviderRegistry;
import pay.conflux.backend.common.error.InvalidOperationStateException;
import pay.conflux.backend.common.error.ValidationException;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.paymentcore.entity.Transaction;
import pay.conflux.backend.paymentcore.entity.TransactionMode;
import pay.conflux.backend.paymentcore.entity.TransactionStatus;
import pay.conflux.backend.paymentcore.entity.WebhookOutbox;
import pay.conflux.backend.paymentcore.events.PaymentRefundedEvent;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.repository.WebhookOutboxRepository;
import pay.conflux.backend.paymentcore.usecase.RefundPaymentRequest;
import pay.conflux.backend.paymentcore.usecase.RefundPaymentResult;
import pay.conflux.backend.provisioning.usecase.CredentialsResolver;

@ExtendWith(MockitoExtension.class)
class RefundPaymentUseCaseImplTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private WebhookOutboxRepository webhookOutboxRepository;
  @Mock private PaymentProviderRegistry paymentProviderRegistry;
  @Mock private CredentialsResolver credentialsResolver;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private PaymentProvider provider;

  @InjectMocks private RefundPaymentUseCaseImpl useCase;

  private UUID originalId;
  private Transaction original;

  @BeforeEach
  void setUp() {
    originalId = UUID.randomUUID();
    original =
        Transaction.builder()
            .id(originalId)
            .businessId(UUID.randomUUID())
            .merchantId(UUID.randomUUID())
            .amountValue(new BigDecimal("100.00"))
            .amountCurrency("BDT")
            .status(TransactionStatus.COMPLETED)
            .vendor("MOCK")
            .mode(TransactionMode.PARTNER)
            .merchantOrderReference("order-1")
            .vendorTransactionId("vendor-trx-1")
            .metadata(new HashMap<>())
            .retryCount(0)
            .build();
  }

  private void stubAdapter(VendorStatus status) {
    when(paymentProviderRegistry.lookup(Vendor.MOCK)).thenReturn(provider);
    when(credentialsResolver.resolveCredentials(original.getBusinessId(), "MOCK"))
        .thenReturn(Map.of("k", "v"));
    when(provider.refund(any(VendorRefundRequest.class), any(VendorCredentials.class)))
        .thenReturn(new VendorResponse(status, "refund-vendor-trx-1", null, null, null));
  }

  @Test
  void execute_happyPath_persistsRefundAndPublishesEvent() {
    when(transactionRepository.findById(originalId)).thenReturn(Optional.of(original));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            inv -> {
              Transaction tx = inv.getArgument(0);
              if (tx.getId() == null) {
                tx.setId(UUID.randomUUID());
              }
              return tx;
            });
    stubAdapter(VendorStatus.COMPLETED);

    RefundPaymentResult result =
        useCase.execute(
            new RefundPaymentRequest(
                originalId, new Money(new BigDecimal("100.00"), "BDT"), "customer request"));

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.originalTransactionId()).isEqualTo(originalId);
    verify(eventPublisher).publishEvent(any(PaymentRefundedEvent.class));
    verify(webhookOutboxRepository, times(1)).save(any(WebhookOutbox.class));
  }

  @Test
  void execute_refundReturnsPending_publishesNoEventOrWebhook() {
    when(transactionRepository.findById(originalId)).thenReturn(Optional.of(original));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            inv -> {
              Transaction tx = inv.getArgument(0);
              if (tx.getId() == null) {
                tx.setId(UUID.randomUUID());
              }
              return tx;
            });
    stubAdapter(VendorStatus.PENDING);

    RefundPaymentResult result =
        useCase.execute(
            new RefundPaymentRequest(
                originalId, new Money(new BigDecimal("100.00"), "BDT"), "customer request"));

    assertThat(result.status()).isEqualTo("PENDING");
    verify(eventPublisher, never()).publishEvent(any(PaymentRefundedEvent.class));
    verify(webhookOutboxRepository, never()).save(any(WebhookOutbox.class));
  }

  @Test
  void execute_nonCompletedOriginal_throwsInvalidOperationState() {
    original.setStatus(TransactionStatus.PENDING);
    when(transactionRepository.findById(originalId)).thenReturn(Optional.of(original));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new RefundPaymentRequest(
                        originalId, new Money(new BigDecimal("10"), "BDT"), "x")))
        .isInstanceOf(InvalidOperationStateException.class);
  }

  @Test
  void execute_amountExceedsOriginal_throwsValidation() {
    when(transactionRepository.findById(originalId)).thenReturn(Optional.of(original));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new RefundPaymentRequest(
                        originalId, new Money(new BigDecimal("500"), "BDT"), "x")))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void execute_currencyMismatch_throwsValidation() {
    when(transactionRepository.findById(originalId)).thenReturn(Optional.of(original));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new RefundPaymentRequest(
                        originalId, new Money(new BigDecimal("10"), "USD"), "x")))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void execute_adapterThrows_marksRefundPendingRecovery() {
    when(transactionRepository.findById(originalId)).thenReturn(Optional.of(original));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            inv -> {
              Transaction tx = inv.getArgument(0);
              if (tx.getId() == null) {
                tx.setId(UUID.randomUUID());
              }
              return tx;
            });
    when(paymentProviderRegistry.lookup(Vendor.MOCK)).thenReturn(provider);
    when(credentialsResolver.resolveCredentials(original.getBusinessId(), "MOCK"))
        .thenReturn(Map.of("k", "v"));
    when(provider.refund(any(), any())).thenThrow(new RuntimeException("vendor down"));

    RefundPaymentResult result =
        useCase.execute(
            new RefundPaymentRequest(
                originalId, new Money(new BigDecimal("10"), "BDT"), "customer request"));

    assertThat(result.status()).isEqualTo("PENDING_RECOVERY");
    verify(eventPublisher, never()).publishEvent(any());
    verify(webhookOutboxRepository, never()).save(any(WebhookOutbox.class));
  }

  @Test
  void execute_vendorReturnsFailed_noEventNoWebhook() {
    when(transactionRepository.findById(originalId)).thenReturn(Optional.of(original));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            inv -> {
              Transaction tx = inv.getArgument(0);
              if (tx.getId() == null) {
                tx.setId(UUID.randomUUID());
              }
              return tx;
            });
    stubAdapter(VendorStatus.FAILED);

    RefundPaymentResult result =
        useCase.execute(
            new RefundPaymentRequest(originalId, new Money(new BigDecimal("10"), "BDT"), "x"));

    assertThat(result.status()).isEqualTo("FAILED");
    verify(eventPublisher, never()).publishEvent(any());
    verify(webhookOutboxRepository, never()).save(any(WebhookOutbox.class));
  }

  @Test
  void execute_vendorReturnsCancelled_landsCancelled() {
    when(transactionRepository.findById(originalId)).thenReturn(Optional.of(original));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            inv -> {
              Transaction tx = inv.getArgument(0);
              if (tx.getId() == null) {
                tx.setId(UUID.randomUUID());
              }
              return tx;
            });
    stubAdapter(VendorStatus.CANCELLED);

    RefundPaymentResult result =
        useCase.execute(
            new RefundPaymentRequest(originalId, new Money(new BigDecimal("10"), "BDT"), "x"));

    assertThat(result.status()).isEqualTo("CANCELLED");
  }

  @Test
  void execute_vendorReturnsUnknown_landsPendingRecovery() {
    when(transactionRepository.findById(originalId)).thenReturn(Optional.of(original));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            inv -> {
              Transaction tx = inv.getArgument(0);
              if (tx.getId() == null) {
                tx.setId(UUID.randomUUID());
              }
              return tx;
            });
    stubAdapter(VendorStatus.UNKNOWN);

    RefundPaymentResult result =
        useCase.execute(
            new RefundPaymentRequest(originalId, new Money(new BigDecimal("10"), "BDT"), "x"));

    assertThat(result.status()).isEqualTo("PENDING_RECOVERY");
  }

  @Test
  void execute_unknownOriginal_throwsResourceNotFound() {
    UUID missingId = UUID.randomUUID();
    when(transactionRepository.findById(missingId)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new RefundPaymentRequest(
                        missingId, new Money(new BigDecimal("10"), "BDT"), "x")))
        .isInstanceOf(pay.conflux.backend.common.error.ResourceNotFoundException.class);
  }

  @Test
  void execute_negativeAmount_throwsValidation() {
    when(transactionRepository.findById(originalId)).thenReturn(Optional.of(original));
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new RefundPaymentRequest(
                        originalId, new Money(new BigDecimal("-1"), "BDT"), "x")))
        .isInstanceOf(ValidationException.class);
  }
}
