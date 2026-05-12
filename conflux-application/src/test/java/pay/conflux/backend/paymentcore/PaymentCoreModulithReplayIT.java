package pay.conflux.backend.paymentcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.common.security.AuthenticatedPrincipal;
import pay.conflux.backend.ledger.listener.PaymentCompletedEventListener;
import pay.conflux.backend.ledger.repository.JournalEntryRepository;
import pay.conflux.backend.ledger.repository.LedgerAccountRepository;
import pay.conflux.backend.ledger.usecase.RecordJournalEntryUseCase;
import pay.conflux.backend.paymentcore.events.PaymentCompletedEvent;
import pay.conflux.backend.paymentcore.usecase.InitiatePaymentRequest;
import pay.conflux.backend.paymentcore.usecase.InitiatePaymentUseCase;
import pay.conflux.backend.paymentcore.usecase.PaymentInitiationResult;

/**
 * Spring Modulith event-replay test. A successful payment initiation publishes a {@code
 * PaymentCompletedEvent}; the ledger's {@code @TransactionalEventListener} crashes on first
 * delivery, which leaves an unprocessed row in {@code event_publication}. Calling {@link
 * IncompleteEventPublications#resubmitIncompletePublications} resubmits the event and the ledger
 * journal entry is recorded on the retry.
 */
@SpringBootTest(classes = pay.conflux.backend.ConfluxPayApplication.class)
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentCoreModulithReplayIT {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("conflux_paycore_replay_test")
          .withUsername("test")
          .withPassword("test");

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void overrides(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    r.add("spring.flyway.enabled", () -> "true");
    r.add("spring.flyway.locations", () -> "classpath:db/migration");
    r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    r.add("spring.modulith.events.jdbc.schema-initialization.enabled", () -> "true");
    r.add("conflux.auth.token-secret", () -> "test-secret-test-secret-test-secret-test-secret");
    r.add("conflux.auth.token-expiration-ms", () -> "3600000");
  }

  @TestConfiguration
  static class FailFirstListenerConfig {
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
            throw new RuntimeException(
                "Simulated downstream-module crash for " + event.transactionId());
          }
          super.handlePaymentCompletedEvent(event);
        }
      };
    }
  }

  @Autowired private InitiatePaymentUseCase initiatePaymentUseCase;
  @Autowired private JournalEntryRepository journalEntryRepository;
  @Autowired private IncompleteEventPublications incompleteEvents;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID seedBusinessAndAuth() {
    UUID userId = UUID.randomUUID();
    UUID businessId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, identifier, identifier_type, password_hash, user_type, status,"
            + " created_at, deleted) VALUES (?, ?, 'EMAIL', ?, 'MERCHANT', 'ACTIVE', now(), false)",
        userId,
        "merchant-" + userId + "@conflux.local",
        "$2a$10$abcdefghijklmnopqrstuv");
    jdbcTemplate.update(
        "INSERT INTO businesses (id, merchant_id, name, display_name, status, created_at,"
            + " updated_at, deleted) VALUES (?, ?, ?, ?, 'ACTIVE', now(), now(), false)",
        businessId,
        userId,
        "biz-" + businessId,
        "Biz");
    jdbcTemplate.update(
        "INSERT INTO vendor_configs (id, business_id, vendor, mode, created_at, updated_at)"
            + " VALUES (?, ?, 'MOCK', 'PARTNER', now(), now())",
        UUID.randomUUID(),
        businessId);

    AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal(
            userId,
            AuthenticatedPrincipal.UserType.MERCHANT,
            userId,
            businessId,
            AuthenticatedPrincipal.Environment.TEST);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
    return businessId;
  }

  @Test
  void initiatedPaymentCompletes_ledgerListenerCrashesOnce_thenReplayRecordsJournalEntry()
      throws InterruptedException {
    UUID businessId = seedBusinessAndAuth();
    String idemKey = "ipk-replay-" + UUID.randomUUID();

    Map<String, String> metadata = new HashMap<>();
    metadata.put("mock_outcome", "success");

    InitiatePaymentRequest req =
        new InitiatePaymentRequest(
            businessId,
            new Money(new BigDecimal("100.00"), "BDT"),
            "MOCK",
            "order-replay",
            "https://merchant.example/return",
            "https://merchant.example/webhook",
            metadata,
            idemKey);

    PaymentInitiationResult result = initiatePaymentUseCase.execute(req);
    assertThat(result.status()).isEqualTo("COMPLETED");

    // Allow the async listener to fire and throw on the first delivery.
    Thread.sleep(800);

    assertThat(
            journalEntryRepository.existsBySourceTypeAndSourceId(
                "PAYMENT", result.transactionId().toString()))
        .as("first delivery crashed → journal entry has NOT been recorded yet")
        .isFalse();

    Long unprocessed =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM event_publication WHERE completion_date IS NULL", Long.class);
    assertThat(unprocessed)
        .as("event_publication has an unprocessed row for the crashed delivery")
        .isNotNull()
        .isGreaterThanOrEqualTo(1L);

    incompleteEvents.resubmitIncompletePublications(e -> true);

    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                journalEntryRepository.existsBySourceTypeAndSourceId(
                    "PAYMENT", result.transactionId().toString()));

    SecurityContextHolder.clearContext();
  }
}
