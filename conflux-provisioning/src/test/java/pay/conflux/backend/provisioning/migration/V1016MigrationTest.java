package pay.conflux.backend.provisioning.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies V1016 successfully drops the inline CHECK constraint that V1012 introduced and replaces
 * it with a named constraint that accepts {@code SSLCOMMERZ}. Uses raw JDBC against a
 * Testcontainers Postgres so we exercise the real CHECK enforcement (no JPA enum mapping involved —
 * the provisioning {@code Vendor} enum is intentionally not extended in this sub-prompt).
 */
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
class V1016MigrationTest {

  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private static JdbcTemplate jdbc;

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration", "classpath:db/test-migration")
        .load()
        .migrate();
    DriverManagerDataSource ds =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    ds.setDriverClassName("org.postgresql.Driver");
    jdbc = new JdbcTemplate(ds);
  }

  @Test
  void sslcommerzVendorIsAccepted() {
    UUID businessId = seedBusiness();
    int rows =
        jdbc.update(
            "INSERT INTO vendor_configs (id, business_id, vendor) VALUES (?, ?, ?)",
            UUID.randomUUID(),
            businessId,
            "SSLCOMMERZ");
    assertThat(rows).isEqualTo(1);
  }

  @Test
  void unknownVendorIsRejected() {
    UUID businessId = seedBusiness();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO vendor_configs (id, business_id, vendor) VALUES (?, ?, ?)",
                    UUID.randomUUID(),
                    businessId,
                    "NOPE"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("vendor_configs_vendor_check");
  }

  @Test
  void constraintIsNamedVendorConfigsVendorCheck() {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint"
                + " WHERE conrelid = 'vendor_configs'::regclass"
                + "   AND conname = 'vendor_configs_vendor_check'",
            Integer.class);
    assertThat(count).isEqualTo(1);
  }

  private static UUID seedBusiness() {
    UUID merchantId = UUID.randomUUID();
    UUID businessId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, status, deleted) VALUES (?, ?, false)", merchantId, "ACTIVE");
    jdbc.update(
        "INSERT INTO businesses (id, merchant_id, name, display_name) VALUES (?, ?, ?, ?)",
        businessId,
        merchantId,
        "Acme",
        "Acme");
    return businessId;
  }
}
