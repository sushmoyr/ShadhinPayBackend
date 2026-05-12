package pay.conflux.backend.paymentcore;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
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
import pay.conflux.backend.paymentcore.entity.IdempotencyRecord;
import pay.conflux.backend.paymentcore.repository.IdempotencyRecordRepository;
import pay.conflux.backend.paymentcore.repository.TransactionRepository;
import pay.conflux.backend.paymentcore.usecase.InitiatePaymentRequest;
import pay.conflux.backend.paymentcore.usecase.InitiatePaymentUseCase;
import pay.conflux.backend.paymentcore.usecase.PaymentInitiationResult;

/**
 * Idempotency load test: 100 concurrent {@code InitiatePaymentUseCase.execute(...)} invocations
 * sharing the same {@code (businessId, idempotencyKey)} must produce exactly one {@code
 * Transaction} row, one {@code IdempotencyRecord}, and 100 byte-identical results.
 *
 * <p>This is the load-bearing invariant of the merchant payments API: a duplicate-key replay must
 * never bill twice. The earlier 8a happy-path replay test exercises the {@code 2-call} case; this
 * test pushes the same guarantee through a 100-way race.
 */
@SpringBootTest(classes = pay.conflux.backend.ConfluxPayApplication.class)
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
@ActiveProfiles("test")
class PaymentInitiationConcurrencyIT {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("conflux_paycore_concurrency_test")
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
  @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID seedBusiness() {
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
    return businessId;
  }

  @Test
  void hundredConcurrentRequestsSameIdempotencyKey_produceExactlyOneTransaction() throws Exception {
    int threadCount = 100;
    int poolSize = 32;

    UUID businessId = seedBusiness();
    String idemKey = "ipk-concurrent-" + UUID.randomUUID();

    long transactionsBefore = transactionRepository.count();
    long idempotencyBefore = idempotencyRecordRepository.count();

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);
    AtomicReferenceArray<PaymentInitiationResult> results = new AtomicReferenceArray<>(threadCount);
    AtomicInteger errors = new AtomicInteger();
    ExecutorService executor = Executors.newFixedThreadPool(poolSize);

    Map<String, String> metadata = new HashMap<>();
    metadata.put("mock_outcome", "default");

    for (int i = 0; i < threadCount; i++) {
      int idx = i;
      executor.submit(
          () -> {
            try {
              startLatch.await();
              InitiatePaymentRequest req =
                  new InitiatePaymentRequest(
                      businessId,
                      new Money(new BigDecimal("250.00"), "BDT"),
                      "MOCK",
                      "order-concurrent",
                      "https://merchant.example/return",
                      "https://merchant.example/webhook",
                      metadata,
                      idemKey);
              results.set(idx, initiatePaymentUseCase.execute(req));
            } catch (Exception e) {
              errors.incrementAndGet();
            } finally {
              endLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    boolean finished = endLatch.await(60, TimeUnit.SECONDS);
    executor.shutdown();
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

    assertThat(finished)
        .as("countDownLatch.await() finished — all 100 threads returned within 60s")
        .isTrue();
    assertThat(errors.get()).as("no use-case invocation threw").isZero();

    long transactionsAfter = transactionRepository.count();
    long idempotencyAfter = idempotencyRecordRepository.count();

    assertThat(transactionsAfter - transactionsBefore)
        .as("Transaction.count == 1 — exactly one row was persisted under the shared idem key")
        .isEqualTo(1L);
    assertThat(idempotencyAfter - idempotencyBefore)
        .as("exactly one IdempotencyRecord row was persisted")
        .isEqualTo(1L);

    PaymentInitiationResult canonical = results.get(0);
    assertThat(canonical).as("first thread returned a non-null result").isNotNull();
    IdempotencyRecord persistedRecord =
        idempotencyRecordRepository
            .findByBusinessIdAndRequestKey(businessId, idemKey)
            .orElseThrow();
    assertThat(canonical.transactionId())
        .as("returned transactionId matches the persisted IdempotencyRecord")
        .isEqualTo(persistedRecord.getTransactionId());

    for (int i = 0; i < threadCount; i++) {
      PaymentInitiationResult r = results.get(i);
      assertThat(r).as("thread %d returned a result", i).isNotNull();
      assertThat(r.transactionId())
          .as("thread %d returned the same transactionId as thread 0", i)
          .isEqualTo(canonical.transactionId());
      assertThat(r.redirectUrl())
          .as("thread %d returned the same redirectUrl as thread 0", i)
          .isEqualTo(canonical.redirectUrl());
      assertThat(r.status())
          .as("thread %d returned the same status as thread 0", i)
          .isEqualTo(canonical.status());
    }
  }
}
