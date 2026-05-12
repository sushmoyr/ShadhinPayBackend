package pay.conflux.backend.paymentcore;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.paymentcore.repository.IdempotencyRecordRepository;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.repository.WebhookOutboxRepository;
import pay.conflux.backend.paymentcore.usecase.InitiatePaymentRequest;
import pay.conflux.backend.paymentcore.usecase.InitiatePaymentUseCase;
import pay.conflux.backend.paymentcore.usecase.PaymentInitiationResult;

@SpringBootTest(classes = pay.conflux.backend.ConfluxPayApplication.class)
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
@ActiveProfiles("test")
class InitiatePaymentIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("conflux_paycore_test")
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

  @Autowired private InitiatePaymentUseCase initiatePaymentUseCase;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private WebhookOutboxRepository webhookOutboxRepository;
  @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID merchantUserId;

  private UUID seedBusiness() {
    UUID userId = UUID.randomUUID();
    UUID businessId = UUID.randomUUID();
    merchantUserId = userId;
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
    return businessId;
  }

  @Test
  void execute_happyPathPersistsAllThreeAggregates() {
    UUID businessId = seedBusiness();
    String idemKey = "ipk-" + UUID.randomUUID();
    Map<String, String> metadata = new HashMap<>();
    metadata.put("mock_outcome", "default");
    metadata.put("merchant_id", merchantUserId.toString());

    InitiatePaymentRequest req =
        new InitiatePaymentRequest(
            businessId,
            new Money(new BigDecimal("250.00"), "BDT"),
            "MOCK",
            "order-" + UUID.randomUUID(),
            "https://merchant.example/return",
            "https://merchant.example/webhook",
            metadata,
            idemKey);

    PaymentInitiationResult result = initiatePaymentUseCase.execute(req);

    assertThat(result.transactionId()).isNotNull();
    assertThat(result.status()).isEqualTo("PENDING");
    assertThat(result.redirectUrl()).startsWith("https://mock.conflux.local/pay/");

    assertThat(transactionRepository.findById(result.transactionId())).isPresent();
    assertThat(webhookOutboxRepository.count()).isEqualTo(1L);
    assertThat(idempotencyRecordRepository.findByBusinessIdAndRequestKey(businessId, idemKey))
        .isPresent();
  }

  @Test
  void execute_replayReturnsSameResultAndDoesNotCreateNewTransaction() {
    UUID businessId = seedBusiness();
    String idemKey = "ipk-replay-" + UUID.randomUUID();

    Map<String, String> metadata = new HashMap<>();
    metadata.put("merchant_id", merchantUserId.toString());
    InitiatePaymentRequest req =
        new InitiatePaymentRequest(
            businessId,
            new Money(new BigDecimal("75.00"), "BDT"),
            "MOCK",
            "order-replay",
            "https://merchant.example/return",
            "https://merchant.example/webhook",
            metadata,
            idemKey);

    long before = transactionRepository.count();
    PaymentInitiationResult first = initiatePaymentUseCase.execute(req);
    long afterFirst = transactionRepository.count();
    PaymentInitiationResult second = initiatePaymentUseCase.execute(req);
    long afterSecond = transactionRepository.count();

    assertThat(afterFirst - before).isEqualTo(1L);
    assertThat(afterSecond).isEqualTo(afterFirst);
    assertThat(second.transactionId()).isEqualTo(first.transactionId());
    assertThat(second.status()).isEqualTo(first.status());
    assertThat(second.redirectUrl()).isEqualTo(first.redirectUrl());
  }

  // Suppress unused warning — kept for symmetry with the other integration tests.
  @SuppressWarnings("unused")
  private LocalDateTime now() {
    return LocalDateTime.now();
  }
}
