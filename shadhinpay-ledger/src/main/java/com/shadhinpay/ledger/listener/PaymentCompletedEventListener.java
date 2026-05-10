package com.shadhinpay.ledger.listener;

import com.shadhinpay.common.money.Money;
import com.shadhinpay.ledger.entity.LedgerAccount;
import com.shadhinpay.ledger.repository.LedgerAccountRepository;
import com.shadhinpay.ledger.usecase.JournalEntryRequest;
import com.shadhinpay.ledger.usecase.PostingRequest;
import com.shadhinpay.ledger.usecase.RecordJournalEntryUseCase;
import com.shadhinpay.paymentcore.events.PaymentCompletedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
              "PAYMENT",
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
