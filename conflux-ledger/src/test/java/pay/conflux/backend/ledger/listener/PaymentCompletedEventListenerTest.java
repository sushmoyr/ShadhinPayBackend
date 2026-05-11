package pay.conflux.backend.ledger.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.ledger.entity.LedgerAccount;
import pay.conflux.backend.ledger.entity.LedgerAccountType;
import pay.conflux.backend.ledger.repository.LedgerAccountRepository;
import pay.conflux.backend.ledger.usecase.JournalEntryRequest;
import pay.conflux.backend.ledger.usecase.PostingRequest;
import pay.conflux.backend.ledger.usecase.RecordJournalEntryUseCase;
import pay.conflux.backend.paymentcore.events.PaymentCompletedEvent;

@ExtendWith(MockitoExtension.class)
class PaymentCompletedEventListenerTest {

  @Mock private RecordJournalEntryUseCase recordJournalEntryUseCase;
  @Mock private LedgerAccountRepository accountRepository;
  @InjectMocks private PaymentCompletedEventListener listener;

  @Captor private ArgumentCaptor<JournalEntryRequest> requestCaptor;

  @Test
  void shouldBuildAndExecuteCorrectJournalEntryRequest() {
    UUID transactionId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    UUID businessId = UUID.randomUUID();
    Instant now = Instant.now();

    PaymentCompletedEvent event =
        new PaymentCompletedEvent(
            transactionId,
            merchantId,
            businessId,
            Money.of(100, "BDT"),
            "BKASH",
            "CASH",
            "ORD-123",
            Map.of(),
            now,
            "trace-123",
            "V-123",
            Money.of(2, "BDT"));

    UUID escrowId = UUID.randomUUID();
    UUID revenueId = UUID.randomUUID();
    LedgerAccount escrow = new LedgerAccount(null, LedgerAccountType.ASSET, "ESCROW", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(escrow, "id", escrowId);
    LedgerAccount revenue =
        new LedgerAccount(null, LedgerAccountType.REVENUE, "PLATFORM_REVENUE", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(revenue, "id", revenueId);

    when(accountRepository.findByCodeAndCurrency("ESCROW", "BDT")).thenReturn(List.of(escrow));
    when(accountRepository.findByCodeAndCurrency("PLATFORM_REVENUE", "BDT"))
        .thenReturn(List.of(revenue));

    listener.handlePaymentCompletedEvent(event);

    verify(recordJournalEntryUseCase).execute(requestCaptor.capture());
    JournalEntryRequest captured = requestCaptor.getValue();

    assertThat(captured.sourceType()).isEqualTo("PAYMENT");
    assertThat(captured.sourceId()).isEqualTo(transactionId.toString());
    assertThat(captured.description()).isEqualTo("Payment captured: ORD-123");
    assertThat(captured.occurredAt()).isEqualTo(now);
    assertThat(captured.postings()).hasSize(3);

    // ESCROW DEBIT
    assertThat(captured.postings().get(0).accountId()).isEqualTo(escrowId);
    assertThat(captured.postings().get(0).amount().amount()).isEqualByComparingTo("100");
    assertThat(captured.postings().get(0).type()).isEqualTo(PostingRequest.Type.DEBIT);

    // MERCHANT_PAYABLE CREDIT
    assertThat(captured.postings().get(1).accountId()).isEqualTo(merchantId);
    assertThat(captured.postings().get(1).amount().amount()).isEqualByComparingTo("-98");
    assertThat(captured.postings().get(1).type()).isEqualTo(PostingRequest.Type.CREDIT);

    // PLATFORM_REVENUE CREDIT
    assertThat(captured.postings().get(2).accountId()).isEqualTo(revenueId);
    assertThat(captured.postings().get(2).amount().amount()).isEqualByComparingTo("-2");
    assertThat(captured.postings().get(2).type()).isEqualTo(PostingRequest.Type.CREDIT);
  }
}
