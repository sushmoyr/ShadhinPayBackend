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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    classes = {com.shadhinpay.ShadhinPayApplication.class, LedgerModulithReplayIT.ReplayConfig.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "spring.datasource.url=jdbc:h2:mem:ledger_replay;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.defer-datasource-initialization=true",
      "spring.modulith.events.jdbc.schema-initialization.enabled=true",
      "spring.sql.init.mode=always",
      "spring.sql.init.data-locations=classpath:ledger-test-seed.sql",
      "shadhinpay.auth.token-secret=test-secret-test-secret-test-secret-test-secret",
      "shadhinpay.auth.token-expiration-ms=3600000",
      "REDIS_HOST=localhost",
      "REDIS_PASSWORD="
    })
@ActiveProfiles("test")
public class LedgerModulithReplayIT {

  @TestConfiguration
  static class ReplayConfig {
    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
      return org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance();
    }

    @Bean
    @Primary
    public PaymentCompletedEventListener wrappedListener(
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
  void testModulithEventReplay() throws InterruptedException {
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
            "trace-123",
            "V-123",
            Money.of(2, "BDT"));

    eventPublisher.publishEvent(event);

    // Give some time for async event handling to fail
    Thread.sleep(1000);

    // Verify it failed and didn't create journal
    assertThat(
            journalEntryRepository.existsBySourceTypeAndSourceId(
                "PAYMENT", transactionId.toString()))
        .isFalse();

    // Now resubmit incomplete events
    incompleteEvents.resubmitIncompletePublications(e -> true);

    // Wait for the async retry to succeed
    await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                journalEntryRepository.existsBySourceTypeAndSourceId(
                    "PAYMENT", transactionId.toString()));

    assertThat(
            journalEntryRepository.existsBySourceTypeAndSourceId(
                "PAYMENT", transactionId.toString()))
        .isTrue();
  }
}
