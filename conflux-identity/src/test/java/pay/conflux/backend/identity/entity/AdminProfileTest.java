package pay.conflux.backend.identity.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.repository.AdminProfileRepository;

/**
 * Round-trips {@link AdminProfile} through Hibernate against a real PostgreSQL container to prove
 * the new {@code admin_tier} column is mapped with {@link jakarta.persistence.EnumType#STRING} and
 * that the Java-side default of {@link AdminTier#VIEWER} matches the migration's column default
 * (Wave D Track 1 sub-prompt 1a).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
@ActiveProfiles("test")
class AdminProfileTest {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private AdminProfileRepository adminProfileRepository;

  @Test
  void persistWithExplicitSuperTier_readsBackAsSuper() {
    AdminProfile profile = newProfile();
    profile.setAdminTier(AdminTier.SUPER);

    AdminProfile saved = adminProfileRepository.save(profile);

    AdminProfile loaded = adminProfileRepository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getAdminTier()).isEqualTo(AdminTier.SUPER);
  }

  @Test
  void persistWithoutExplicitTier_defaultsToViewer() {
    AdminProfile profile = newProfile();

    AdminProfile saved = adminProfileRepository.save(profile);

    AdminProfile loaded = adminProfileRepository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getAdminTier()).isEqualTo(AdminTier.VIEWER);
  }

  @Test
  void persistWithManagerTier_readsBackAsManager() {
    AdminProfile profile = newProfile();
    profile.setAdminTier(AdminTier.MANAGER);

    AdminProfile saved = adminProfileRepository.save(profile);

    AdminProfile loaded = adminProfileRepository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getAdminTier()).isEqualTo(AdminTier.MANAGER);
  }

  private AdminProfile newProfile() {
    AdminProfile profile = new AdminProfile();
    profile.setId(UUID.randomUUID());
    profile.setUserId(UUID.randomUUID());
    profile.setDepartment("Platform");
    profile.setEmployeeId("EMP-" + UUID.randomUUID());
    return profile;
  }
}
