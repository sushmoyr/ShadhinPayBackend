package pay.conflux.backend.ledger.listener;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.ledger.entity.JournalSourceType;
import pay.conflux.backend.ledger.entity.LedgerAccount;
import pay.conflux.backend.ledger.repository.LedgerAccountRepository;
import pay.conflux.backend.ledger.usecase.JournalEntryRequest;
import pay.conflux.backend.ledger.usecase.PostingRequest;
import pay.conflux.backend.ledger.usecase.RecordJournalEntryUseCase;
import pay.conflux.backend.paymentcore.events.PaymentCompletedEvent;

@Component
@RequiredArgsConstructor
public class PaymentCompletedEventListener {

  private final RecordJournalEntryUseCase recordJournalEntryUseCase;
  private final LedgerAccountRepository accountRepository;

  @Async("ledgerEventExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handlePaymentCompletedEvent(PaymentCompletedEvent event) {
    MDC.put("traceId", event.traceId());
    try {
      LedgerAccount escrowLogic = accountRepository.findByCodeAndCurrency("ESCROW", "BDT").get(0);
      LedgerAccount revenueLogic =
          accountRepository.findByCodeAndCurrency("PLATFORM_REVENUE", "BDT").get(0);

      Money merchantAmount = event.amount().subtract(event.platformFee());

      JournalEntryRequest request =
          new JournalEntryRequest(
              JournalSourceType.PAYMENT.name(),
              event.transactionId().toString(),
              "Payment captured: " + event.merchantOrderReference(),
              List.of(
                  new PostingRequest(
                      escrowLogic.getId(), event.amount(), PostingRequest.Type.DEBIT),
                  new PostingRequest(
                      event.merchantId(), merchantAmount.negate(), PostingRequest.Type.CREDIT),
                  new PostingRequest(
                      revenueLogic.getId(),
                      event.platformFee().negate(),
                      PostingRequest.Type.CREDIT)),
              event.occurredAt());

      recordJournalEntryUseCase.execute(request);
    } finally {
      MDC.remove("traceId");
    }
  }
}
