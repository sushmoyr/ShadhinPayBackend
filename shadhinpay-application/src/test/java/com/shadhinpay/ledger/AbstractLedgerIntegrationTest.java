package com.shadhinpay.ledger;

import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared base for ledger integration tests: spins up a single Postgres 16 container, runs Flyway
 * migrations (V1002 schema + V1003 system-account seed), and switches Hibernate to {@code validate}
 * so any entity/schema drift fails fast.
 */
@SpringBootTest(classes = com.shadhinpay.ShadhinPayApplication.class)
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
@ActiveProfiles("test")
public abstract class AbstractLedgerIntegrationTest {

  @Container
  protected static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("shadhinpay_ledger_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void overrides(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    r.add("spring.flyway.enabled", () -> "true");
    r.add("spring.flyway.locations", () -> "classpath:db/migration");
    r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    r.add("spring.modulith.events.jdbc.schema-initialization.enabled", () -> "true");
    r.add(
        "spring.autoconfigure.exclude",
        () ->
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
    r.add("shadhinpay.auth.token-secret", () -> "test-secret-test-secret-test-secret-test-secret");
    r.add("shadhinpay.auth.token-expiration-ms", () -> "3600000");
  }
}
