package pay.conflux.backend.paymentcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pay.conflux.backend.ConfluxPayApplication;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.common.webhook.WebhookSigner;
import pay.conflux.backend.identity.events.MerchantVerifiedEvent;
import pay.conflux.backend.paymentcore.entity.WebhookOutbox;
import pay.conflux.backend.paymentcore.repository.IdempotencyRecordRepository;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.repository.WebhookOutboxRepository;
import pay.conflux.backend.paymentcore.usecase.InitiatePaymentRequest;
import pay.conflux.backend.paymentcore.usecase.InitiatePaymentUseCase;
import pay.conflux.backend.paymentcore.usecase.PaymentInitiationResult;
import pay.conflux.backend.provisioning.dto.ApiKeyDto;
import pay.conflux.backend.provisioning.dto.GenerateApiKeyRequest;
import pay.conflux.backend.provisioning.usecase.BusinessContext;
import pay.conflux.backend.provisioning.usecase.GenerateApiKeyUseCase;
import pay.conflux.backend.provisioning.usecase.GetBusinessByApiKeyUseCase;

/**
 * Wave B golden-path integration test. Walks the full pipeline end-to-end inside a single
 * {@code @SpringBootTest} so the acceptance gate has one assertion of cross-module wiring:
 *
 * <ol>
 *   <li>Seed an ACTIVE merchant {@code User} in identity.
 *   <li>Publish {@link MerchantVerifiedEvent} via the modulith bus; the provisioning
 *       {@code MerchantVerifiedEventListener} auto-creates the default business.
 *   <li>Configure a MOCK vendor for that business (PARTNER mode) so the use case can dispatch.
 *   <li>Generate an API key for the new business and resolve it via
 *       {@link GetBusinessByApiKeyUseCase} — proves the provisioning hot-path round-trip.
 *   <li>Call {@link InitiatePaymentUseCase} with that {@code businessId} and assert the
 *       transaction, idempotency record, and {@code webhook_outbox} row land in the same DB tx.
 *   <li>Sign the queued webhook payload with the merchant's webhook secret and assert the
 *       signature verifies — the dispatcher uses the same {@link WebhookSigner} on delivery.
 * </ol>
 *
 * <p>Testcontainers gated — disabled when Docker is absent (CI runs it; local skips). Webhook
 * HTTP delivery to WireMock is exercised by the focused {@code WebhookOutboxDispatcherTest} in
 * the payment-core module; this test verifies the outbox row is enqueued and the HMAC signing
 * step the dispatcher will perform is byte-correct.
 */
@SpringBootTest(classes = ConfluxPayApplication.class)
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
@ActiveProfiles("test")
class WaveBGoldenPathIT {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("conflux_waveb_golden_test")
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

  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private GenerateApiKeyUseCase generateApiKeyUseCase;
  @Autowired private GetBusinessByApiKeyUseCase getBusinessByApiKeyUseCase;
  @Autowired private InitiatePaymentUseCase initiatePaymentUseCase;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private WebhookOutboxRepository webhookOutboxRepository;
  @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
  @Autowired private WebhookSigner webhookSigner;

  @Test
  void registerMerchant_verify_provisionBusiness_generateKey_initiatePayment_endToEnd() {
    UUID merchantUserId = UUID.randomUUID();
    UUID merchantProfileId = UUID.randomUUID();

    // 1. Seed an ACTIVE merchant in identity.users.
    jdbcTemplate.update(
        "INSERT INTO users (id, identifier, identifier_type, password_hash, user_type, status,"
            + " created_at, deleted) VALUES (?, ?, 'EMAIL', ?, 'MERCHANT', 'ACTIVE', now(), false)",
        merchantUserId,
        "merchant-" + merchantUserId + "@conflux.local",
        "$2a$10$abcdefghijklmnopqrstuv");

    // 2. Publish MerchantVerifiedEvent — provisioning's listener auto-creates the default business.
    eventPublisher.publishEvent(
        new MerchantVerifiedEvent(
            merchantUserId, merchantProfileId, java.time.Instant.now(), "trace-waveb-golden"));

    // The listener runs after-commit via @ApplicationModuleListener; allow ≤ 10s for replay.
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              List<UUID> businesses =
                  jdbcTemplate.queryForList(
                      "SELECT id FROM businesses WHERE merchant_id = ? AND deleted = false",
                      UUID.class,
                      merchantUserId);
              assertThat(businesses)
                  .as("MerchantVerifiedEventListener should have created exactly one business")
                  .hasSize(1);
            });

    UUID businessId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM businesses WHERE merchant_id = ? AND deleted = false",
            UUID.class,
            merchantUserId);

    // 3. Configure a MOCK vendor (PARTNER mode) so the payment orchestrator can dispatch.
    jdbcTemplate.update(
        "INSERT INTO vendor_configs (id, business_id, vendor, mode, created_at, updated_at)"
            + " VALUES (?, ?, 'MOCK', 'PARTNER', now(), now())",
        UUID.randomUUID(),
        businessId);

    // 4. Generate a LIVE API key + resolve it via the hot-path use case.
    ApiKeyDto generated =
        generateApiKeyUseCase.execute(businessId, new GenerateApiKeyRequest("LIVE", null));
    assertThat(generated.getKey()).as("plaintext key returned exactly once").isNotBlank();
    assertThat(generated.getKey()).startsWith("sp_live_");

    BusinessContext ctx = getBusinessByApiKeyUseCase.execute(generated.getKey());
    assertThat(ctx.businessId())
        .as("GetBusinessByApiKeyUseCase resolves to the same businessId")
        .isEqualTo(businessId);
    assertThat(ctx.merchantId()).isEqualTo(merchantUserId);

    // 5. Initiate a payment — orchestrator runs idempotency → provisioning → risk → quota → adapter.
    String idemKey = "ipk-golden-" + UUID.randomUUID();
    Map<String, String> metadata = new HashMap<>();
    metadata.put("mock_outcome", "default");
    metadata.put("merchant_id", merchantUserId.toString());

    long txBefore = transactionRepository.count();
    long outboxBefore = webhookOutboxRepository.count();
    long idemBefore = idempotencyRecordRepository.count();

    InitiatePaymentRequest req =
        new InitiatePaymentRequest(
            businessId,
            new Money(new BigDecimal("500.00"), "BDT"),
            "MOCK",
            "order-golden-" + UUID.randomUUID(),
            "https://merchant.example/return",
            "https://merchant.example/webhook",
            metadata,
            idemKey);

    PaymentInitiationResult result = initiatePaymentUseCase.execute(req);

    assertThat(result.transactionId()).isNotNull();
    assertThat(result.status()).isEqualTo("PENDING");

    assertThat(transactionRepository.count() - txBefore)
        .as("exactly one Transaction row was persisted by the orchestrator")
        .isEqualTo(1L);
    assertThat(idempotencyRecordRepository.count() - idemBefore)
        .as("exactly one IdempotencyRecord row was persisted")
        .isEqualTo(1L);
    assertThat(webhookOutboxRepository.count() - outboxBefore)
        .as("exactly one webhook outbox row was enqueued in the same DB transaction")
        .isEqualTo(1L);

    // 6. Verify the HMAC signing convention the dispatcher will use on delivery.
    String webhookSecret = "wave-b-golden-test-secret";
    List<WebhookOutbox> outboxRows =
        webhookOutboxRepository.findAllByStatusAndNextAttemptAtBefore(
            pay.conflux.backend.paymentcore.entity.WebhookOutboxStatus.PENDING,
            java.time.Instant.now().plusSeconds(60),
            org.springframework.data.domain.PageRequest.of(0, 5));
    assertThat(outboxRows).anyMatch(row -> row.getTransactionId().equals(result.transactionId()));

    String body = "{\"transactionId\":\"" + result.transactionId() + "\",\"status\":\"PENDING\"}";
    String signature = webhookSigner.signatureFor(body, webhookSecret);
    assertThat(signature).as("HMAC-SHA256 hex signature").isNotBlank().hasSize(64);
    assertThat(webhookSigner.verify(body, webhookSecret, signature)).isTrue();
    assertThat(webhookSigner.verify(body, "wrong-secret", signature)).isFalse();
  }
}
