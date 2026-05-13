package pay.conflux.backend.identity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pay.conflux.backend.ConfluxPayApplication;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.enums.UserType;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;

/**
 * Boots the application context once per scenario against a real Testcontainers Postgres so that
 * the {@link SuperAdminBootstrap} runner actually executes. Covers the five scenarios from {@code
 * DOCS/features/identity/TECH_SPEC.md §5}.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
class SuperAdminBootstrapIT {

  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @BeforeEach
  void cleanDatabase() {
    Flyway flyway =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load();
    flyway.clean();
    flyway.migrate();
  }

  @Test
  void scenario1_freshDbNoEnvVarsFailsFast() {
    assertThatThrownBy(() -> bootContext("", ""))
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SUPER admin");
  }

  @Test
  void scenario2_freshDbWithEnvVarsCreatesExactlyOneSuper() {
    try (ConfigurableApplicationContext ctx =
        bootContext("scenario2-super@example.com", "scenario2-password")) {
      UserRepository users = ctx.getBean(UserRepository.class);
      AdminProfileRepository profiles = ctx.getBean(AdminProfileRepository.class);
      PasswordEncoder encoder = ctx.getBean(PasswordEncoder.class);

      assertThat(profiles.countActiveSuperAdmins()).isEqualTo(1L);

      User created =
          users
              .findByIdentifierAndIdentifierTypeAndDeletedFalse(
                  "scenario2-super@example.com", IdentifierType.EMAIL)
              .orElseThrow();
      assertThat(created.getUserType()).isEqualTo(UserType.ADMIN);
      assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
      assertThat(encoder.matches("scenario2-password", created.getPasswordHash())).isTrue();

      AdminProfile p = profiles.findByUserId(created.getId()).orElseThrow();
      assertThat(p.getAdminTier()).isEqualTo(AdminTier.SUPER);
    }
  }

  @Test
  void scenario3_rebootWithSameEnvVarsLeavesHashUnchanged() {
    String hashAfterBoot1;
    UUID userIdAfterBoot1;
    try (ConfigurableApplicationContext ctx =
        bootContext("scenario3-super@example.com", "scenario3-password")) {
      User u =
          ctx.getBean(UserRepository.class)
              .findByIdentifierAndIdentifierTypeAndDeletedFalse(
                  "scenario3-super@example.com", IdentifierType.EMAIL)
              .orElseThrow();
      hashAfterBoot1 = u.getPasswordHash();
      userIdAfterBoot1 = u.getId();
    }

    try (ConfigurableApplicationContext ctx =
        bootContext("scenario3-super@example.com", "scenario3-password")) {
      User u =
          ctx.getBean(UserRepository.class)
              .findByIdentifierAndIdentifierTypeAndDeletedFalse(
                  "scenario3-super@example.com", IdentifierType.EMAIL)
              .orElseThrow();
      assertThat(u.getId()).isEqualTo(userIdAfterBoot1);
      assertThat(u.getPasswordHash()).isEqualTo(hashAfterBoot1);
      assertThat(ctx.getBean(AdminProfileRepository.class).countActiveSuperAdmins()).isEqualTo(1L);
    }
  }

  @Test
  void scenario4_rebootWithDifferentPasswordRotatesHash() {
    String hashAfterBoot1;
    try (ConfigurableApplicationContext ctx =
        bootContext("scenario4-super@example.com", "original-password")) {
      hashAfterBoot1 =
          ctx.getBean(UserRepository.class)
              .findByIdentifierAndIdentifierTypeAndDeletedFalse(
                  "scenario4-super@example.com", IdentifierType.EMAIL)
              .orElseThrow()
              .getPasswordHash();
    }

    try (ConfigurableApplicationContext ctx =
        bootContext("scenario4-super@example.com", "rotated-password")) {
      User u =
          ctx.getBean(UserRepository.class)
              .findByIdentifierAndIdentifierTypeAndDeletedFalse(
                  "scenario4-super@example.com", IdentifierType.EMAIL)
              .orElseThrow();
      PasswordEncoder encoder = ctx.getBean(PasswordEncoder.class);
      assertThat(u.getPasswordHash()).isNotEqualTo(hashAfterBoot1);
      assertThat(encoder.matches("rotated-password", u.getPasswordHash())).isTrue();
      assertThat(encoder.matches("original-password", u.getPasswordHash())).isFalse();
    }
  }

  @Test
  void scenario5_preExistingSuperWithBlankEnvVarsBootsSuccessfully() {
    JdbcTemplate jdbc = new JdbcTemplate(newDataSource());
    UUID userId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    String preHash = "$2a$10$AAAAAAAAAAAAAAAAAAAAAA";
    jdbc.update(
        "INSERT INTO users(id, identifier, identifier_type, password_hash, user_type, status,"
            + " mfa_enabled) VALUES (?, ?, 'EMAIL', ?, 'ADMIN', 'ACTIVE', false)",
        userId,
        "seeded-super@example.com",
        preHash);
    jdbc.update(
        "INSERT INTO admin_profiles(id, user_id, department, employee_id, admin_tier) "
            + "VALUES (?, ?, 'Seeded', ?, 'SUPER')",
        profileId,
        userId,
        "EMP-SEED-" + UUID.randomUUID());

    try (ConfigurableApplicationContext ctx = bootContext("", "")) {
      AdminProfileRepository profiles = ctx.getBean(AdminProfileRepository.class);
      assertThat(profiles.countActiveSuperAdmins()).isEqualTo(1L);
      AdminProfile p = profiles.findByUserId(userId).orElseThrow();
      assertThat(p.getAdminTier()).isEqualTo(AdminTier.SUPER);
    }
  }

  private static ConfigurableApplicationContext bootContext(String identifier, String password) {
    return new SpringApplicationBuilder(ConfluxPayApplication.class)
        .profiles("test")
        .properties(propertiesFor(identifier, password).toArray(new String[0]))
        .web(org.springframework.boot.WebApplicationType.NONE)
        .run();
  }

  private static List<String> propertiesFor(String identifier, String password) {
    return List.of(
        "spring.datasource.url=" + postgres.getJdbcUrl(),
        "spring.datasource.username=" + postgres.getUsername(),
        "spring.datasource.password=" + postgres.getPassword(),
        "spring.flyway.enabled=false",
        "conflux.identity.super-admin.identifier=" + identifier,
        "conflux.identity.super-admin.password=" + password);
  }

  private static org.springframework.jdbc.datasource.DriverManagerDataSource newDataSource() {
    org.springframework.jdbc.datasource.DriverManagerDataSource ds =
        new org.springframework.jdbc.datasource.DriverManagerDataSource();
    ds.setDriverClassName("org.postgresql.Driver");
    ds.setUrl(postgres.getJdbcUrl());
    ds.setUsername(postgres.getUsername());
    ds.setPassword(postgres.getPassword());
    return ds;
  }
}
