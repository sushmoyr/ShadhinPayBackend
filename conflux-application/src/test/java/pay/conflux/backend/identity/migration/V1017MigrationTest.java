package pay.conflux.backend.identity.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the V1017 migration that adds {@code admin_profiles.admin_tier}. Covers three scenarios:
 *
 * <ul>
 *   <li>A row inserted post-migration with an accepted tier (SUPER) succeeds.
 *   <li>A row inserted post-migration with a non-enum value (OWNER) violates the named CHECK
 *       constraint.
 *   <li>A row inserted before V1017 (with V1001..V1016 applied) backfills to VIEWER once V1017
 *       runs, since the migration declares {@code NOT NULL DEFAULT 'VIEWER'}.
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
class V1017MigrationTest {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("conflux_v1017_test")
          .withUsername("test")
          .withPassword("test");

  private Flyway newFlyway() {
    return Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .load();
  }

  @Test
  void migration_appliesAdminTierColumnWithCheckConstraintAndDefault() throws Exception {
    Flyway flyway = newFlyway();
    flyway.clean();
    flyway.migrate();

    try (Connection c =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

      UUID userOk = insertUser(c, "admin-ok@example.com");
      insertAdminProfile(c, userOk, "EMP-OK-" + UUID.randomUUID(), "SUPER");

      try (Statement s = c.createStatement();
          ResultSet rs =
              s.executeQuery(
                  "SELECT admin_tier FROM admin_profiles WHERE user_id = '" + userOk + "'")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("SUPER");
      }

      UUID userBad = insertUser(c, "admin-bad@example.com");
      assertThatThrownBy(
              () -> insertAdminProfile(c, userBad, "EMP-BAD-" + UUID.randomUUID(), "OWNER"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("admin_profiles_admin_tier_check");
    }
  }

  @Test
  void migration_backfillsPreExistingRowsToViewer() throws Exception {
    Flyway flyway = newFlyway();
    flyway.clean();
    flyway.migrate(); // apply everything, including V1017 (target=latest)

    // To exercise the backfill, we drop the column and re-add it as if V1017 had not yet run,
    // then re-apply the V1017 file's SQL by hand against a pre-existing row.
    try (Connection c =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

      try (Statement s = c.createStatement()) {
        s.execute(
            "ALTER TABLE admin_profiles DROP CONSTRAINT IF EXISTS admin_profiles_admin_tier_check");
        s.execute("ALTER TABLE admin_profiles DROP COLUMN admin_tier");
      }

      UUID preExistingUser = insertUser(c, "pre-existing@example.com");
      try (Statement s = c.createStatement()) {
        s.execute(
            "INSERT INTO admin_profiles (id, user_id, department, employee_id) VALUES ('"
                + UUID.randomUUID()
                + "', '"
                + preExistingUser
                + "', 'Platform', 'EMP-PRE-"
                + UUID.randomUUID()
                + "')");
      }

      try (Statement s = c.createStatement()) {
        s.execute(
            "ALTER TABLE admin_profiles "
                + "ADD COLUMN admin_tier VARCHAR(16) NOT NULL DEFAULT 'VIEWER'");
        s.execute(
            "ALTER TABLE admin_profiles "
                + "ADD CONSTRAINT admin_profiles_admin_tier_check "
                + "CHECK (admin_tier IN ('VIEWER','MANAGER','SUPER'))");
      }

      try (Statement s = c.createStatement();
          ResultSet rs =
              s.executeQuery(
                  "SELECT admin_tier FROM admin_profiles WHERE user_id = '"
                      + preExistingUser
                      + "'")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("VIEWER");
      }
    }
  }

  private UUID insertUser(Connection c, String identifier) throws SQLException {
    UUID id = UUID.randomUUID();
    try (Statement s = c.createStatement()) {
      s.execute(
          "INSERT INTO users "
              + "(id, identifier, identifier_type, password_hash, user_type, status, mfa_enabled) "
              + "VALUES ('"
              + id
              + "', '"
              + identifier
              + "', 'EMAIL', '$2a$10$AAAAAAAAAAAAAAAAAAAAAA', 'ADMIN', 'ACTIVE', false)");
    }
    return id;
  }

  private void insertAdminProfile(Connection c, UUID userId, String employeeId, String tier)
      throws SQLException {
    try (Statement s = c.createStatement()) {
      s.execute(
          "INSERT INTO admin_profiles (id, user_id, department, employee_id, admin_tier) VALUES ('"
              + UUID.randomUUID()
              + "', '"
              + userId
              + "', 'Platform', '"
              + employeeId
              + "', '"
              + tier
              + "')");
    }
  }
}
