package pay.conflux.backend.quota;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pay.conflux.backend.quota.usecase.QuotaReservation;
import pay.conflux.backend.quota.usecase.ReserveQuotaUseCase;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class QuotaResiliencyIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("conflux.quota.free-per-month", () -> 10);
  }

  @Autowired private ReserveQuotaUseCase reserveUseCase;

  @Test
  void testResiliency_FailOpen() {
    LogCaptor logCaptor =
        LogCaptor.forClass(pay.conflux.backend.quota.usecase.impl.ReserveQuotaUseCaseImpl.class);

    redis.stop(); // Shutdown Redis mid-test

    try {
      UUID merchantId = UUID.randomUUID();
      QuotaReservation res = reserveUseCase.execute(merchantId);

      assertThat(res.status()).isEqualTo(QuotaReservation.Status.FREE);
      assertThat(logCaptor.getErrorLogs())
          .anyMatch(log -> log.contains("Failed to reserve quota") && log.contains("Failing OPEN"));
    } finally {
      redis.start(); // Restart for subsequent tests if any
      logCaptor.close();
    }
  }
}
