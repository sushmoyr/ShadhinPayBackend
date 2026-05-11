package pay.conflux.backend.provisioning.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pay.conflux.backend.provisioning.constant.BusinessStatus;
import pay.conflux.backend.provisioning.constant.Environment;
import pay.conflux.backend.provisioning.constant.Vendor;
import pay.conflux.backend.provisioning.constant.VendorMode;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.entity.VendorConfig;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
@ActiveProfiles("test")
class ProvisioningRepositoryIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void registerProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Configuration
  @EnableAutoConfiguration
  @EntityScan(basePackages = "pay.conflux.backend.provisioning.entity")
  @EnableJpaRepositories(basePackages = "pay.conflux.backend.provisioning.repository")
  @ComponentScan(basePackages = "pay.conflux.backend.common")
  static class TestConfig {}

  @Autowired private BusinessRepository businessRepository;
  @Autowired private VendorConfigRepository vendorConfigRepository;
  @Autowired private ApiKeyRepository apiKeyRepository;
  @Autowired private DataSource dataSource;

  @Test
  void migrationsApplyAndRepositoriesPersist() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    UUID merchantId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, status, deleted) VALUES (?, ?, false)", merchantId, "ACTIVE");

    Business b = new Business();
    b.setMerchantId(merchantId);
    b.setName("Acme");
    b.setDisplayName("Acme");
    b.setStatus(BusinessStatus.ACTIVE);
    Business saved = businessRepository.save(b);
    assertThat(saved.getId()).isNotNull();

    assertThat(businessRepository.findByMerchantIdAndDeletedFalse(merchantId)).hasSize(1);
    assertThat(businessRepository.existsByMerchantIdAndDeletedFalse(merchantId)).isTrue();

    VendorConfig vc = new VendorConfig();
    vc.setBusinessId(saved.getId());
    vc.setVendor(Vendor.BKASH);
    vc.setMode(VendorMode.PARTNER);
    vendorConfigRepository.save(vc);
    assertThat(vendorConfigRepository.findByBusinessIdAndVendor(saved.getId(), Vendor.BKASH))
        .isPresent();
    assertThat(vendorConfigRepository.findAllByBusinessId(saved.getId())).hasSize(1);

    ApiKey key = new ApiKey();
    key.setBusinessId(saved.getId());
    key.setKeyHash("hash-" + UUID.randomUUID());
    key.setKeyPrefix("sp_test_");
    key.setLastFour("abcd");
    key.setEnvironment(Environment.TEST);
    key.setRevoked(false);
    ApiKey savedKey = apiKeyRepository.save(key);
    assertThat(apiKeyRepository.findByKeyHashAndRevokedFalse(savedKey.getKeyHash())).isPresent();
    assertThat(apiKeyRepository.findAllByBusinessIdAndRevokedFalse(saved.getId())).hasSize(1);
    assertThat(apiKeyRepository.findAllByBusinessIdAndEnvironment(saved.getId(), Environment.TEST))
        .hasSize(1);

    int updated =
        apiKeyRepository.markLastUsedAt(savedKey.getId(), Instant.parse("2026-05-11T12:00:00Z"));
    assertThat(updated).isEqualTo(1);
  }
}
