package pay.conflux.backend.quota;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pay.conflux.backend.quota.cache.QuotaCachePort;

/**
 * Verifies that a pending reservation whose pending-TTL elapses before confirm is treated as a
 * no-op (the Redis key is gone, confirmReservation returns false, and the final counter is never
 * incremented). Overrides the pending TTL to 1 second via @DynamicPropertySource.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class QuotaTtlExpiryIT {

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
    // Force a 1-second pending TTL so the test can wait it out.
    registry.add("conflux.quota.pending-ttl-seconds", () -> 1);
  }

  @Autowired private QuotaCachePort cachePort;

  @Test
  void confirmAfterTtlExpiry_isNoOp() throws InterruptedException {
    UUID merchantId = UUID.randomUUID();
    String period = "2026-05";

    String resId = cachePort.reservePending(merchantId, period);
    assertThat(cachePort.countPending(merchantId, period)).isEqualTo(1);

    // Wait past the 1-second TTL.
    Thread.sleep(2000);

    assertThat(cachePort.countPending(merchantId, period)).isZero();
    assertThat(cachePort.confirmReservation(merchantId, period, resId)).isFalse();
    assertThat(cachePort.getFinalCount(merchantId, period)).isZero();
  }
}
