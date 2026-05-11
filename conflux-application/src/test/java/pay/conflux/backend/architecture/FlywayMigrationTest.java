package pay.conflux.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
class FlywayMigrationTest {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("conflux_migration_test")
          .withUsername("test")
          .withPassword("test");

  @Test
  void modulithEventPublicationMigrationApplies() throws Exception {
    Flyway flyway =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .load();
    int applied = flyway.migrate().migrationsExecuted;
    assertThat(applied).isGreaterThanOrEqualTo(1);
    assertEventPublicationSchema(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  static void assertEventPublicationSchema(String jdbcUrl, String user, String password)
      throws Exception {
    try (Connection c = DriverManager.getConnection(jdbcUrl, user, password);
        var stmt = c.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "select column_name from information_schema.columns "
                    + "where table_name = 'event_publication' order by ordinal_position")) {
      var columns = new ArrayList<String>();
      while (rs.next()) columns.add(rs.getString(1));
      assertThat(columns)
          .containsExactly(
              "id",
              "listener_id",
              "event_type",
              "serialized_event",
              "publication_date",
              "completion_date");
    }
  }
}
