package com.shadhinpay.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.shadhinpay.common.money.Money;
import com.shadhinpay.ledger.listener.PaymentCompletedEventListener;
import com.shadhinpay.ledger.repository.JournalEntryRepository;
import com.shadhinpay.ledger.repository.LedgerAccountRepository;
import com.shadhinpay.ledger.usecase.RecordJournalEntryUseCase;
import com.shadhinpay.paymentcore.events.PaymentCompletedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LedgerModulithReplayIT extends AbstractLedgerIntegrationTest {

  @TestConfiguration
  static class ReplayConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
      return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    @Primary
    PaymentCompletedEventListener wrappedListener(
        RecordJournalEntryUseCase recordJournalEntryUseCase,
        LedgerAccountRepository accountRepository) {
      return new PaymentCompletedEventListener(recordJournalEntryUseCase, accountRepository) {
        private final Map<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();

        @Override
        public void handlePaymentCompletedEvent(PaymentCompletedEvent event) {
          int attempt =
              attempts
                  .computeIfAbsent(event.transactionId(), k -> new AtomicInteger(0))
                  .incrementAndGet();
          if (attempt == 1) {
            throw new RuntimeException("Simulated failure for " + event.transactionId());
          }
          super.handlePaymentCompletedEvent(event);
        }
      };
    }
  }

  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private JournalEntryRepository journalEntryRepository;
  @Autowired private IncompleteEventPublications incompleteEvents;

  @Test
  void incompletePublicationReplaySucceeds() throws InterruptedException {
    UUID transactionId = UUID.randomUUID();
    PaymentCompletedEvent event =
        new PaymentCompletedEvent(
            transactionId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Money.of(100, "BDT"),
            "BKASH",
            "CASH",
            "ORD-123",
            Map.of(),
            Instant.now(),
            "trace-replay",
            "V-replay",
            Money.of(2, "BDT"));

    eventPublisher.publishEvent(event);

    // Allow the async listener to throw on first invocation.
    Thread.sleep(500);

    assertThat(
            journalEntryRepository.existsBySourceTypeAndSourceId(
                "PAYMENT", transactionId.toString()))
        .isFalse();

    incompleteEvents.resubmitIncompletePublications(e -> true);

    await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                journalEntryRepository.existsBySourceTypeAndSourceId(
                    "PAYMENT", transactionId.toString()));
  }
}
