package com.shadhinpay.quota;

import static org.assertj.core.api.Assertions.assertThat;

import com.shadhinpay.quota.job.LeakedReservationCleanupJob;
import com.shadhinpay.quota.job.MonthlyPersistenceJob;
import com.shadhinpay.quota.repository.QuotaUsageRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class QuotaJobsIT {

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
    registry.add("shadhinpay.quota.free-per-month", () -> 10);
  }

  @Autowired private MonthlyPersistenceJob monthlyPersistenceJob;

  @Autowired private LeakedReservationCleanupJob leakedReservationCleanupJob;

  @Autowired private StringRedisTemplate redisTemplate;

  @Autowired private QuotaUsageRepository quotaUsageRepository;

  @Test
  void monthlyPersistence_readsFromRedisAndUpserts() {
    UUID m1 = UUID.randomUUID();
    UUID m2 = UUID.randomUUID();
    String period = "2026-04";

    // Seed Redis
    redisTemplate.opsForValue().set("quota:final:" + m1 + ":" + period, "7");
    redisTemplate.opsForValue().set("quota:final:" + m2 + ":" + period, "12");

    // Run Job
    monthlyPersistenceJob.runForPeriod(period);

    // Assert DB
    var usage1 = quotaUsageRepository.findByMerchantIdAndPeriod(m1, period).orElseThrow();
    assertThat(usage1.getPartnerModeCount()).isEqualTo(7);

    var usage2 = quotaUsageRepository.findByMerchantIdAndPeriod(m2, period).orElseThrow();
    assertThat(usage2.getPartnerModeCount()).isEqualTo(12);

    // Re-run job to test idempotency
    redisTemplate
        .opsForValue()
        .set("quota:final:" + m1 + ":" + period, "9"); // Simulating late update
    monthlyPersistenceJob.runForPeriod(period);

    var usage1Updated = quotaUsageRepository.findByMerchantIdAndPeriod(m1, period).orElseThrow();
    assertThat(usage1Updated.getPartnerModeCount()).isEqualTo(9); // Upserted!
  }

  @Test
  void leakedReservationCleanup_removesKeysWithoutExpiry() {
    UUID m1 = UUID.randomUUID();
    String period = "2026-05";
    String res1 = UUID.randomUUID().toString();
    String res2 = UUID.randomUUID().toString();

    String key1 = "quota:pending:" + m1 + ":" + period + ":" + res1;
    String key2 = "quota:pending:" + m1 + ":" + period + ":" + res2;

    // Seed Redis - key1 without expiry
    redisTemplate.opsForValue().set(key1, "1");
    // Seed Redis - key2 with 30m expiry
    redisTemplate.opsForValue().set(key2, "1", java.time.Duration.ofMinutes(30));

    // Run Job
    leakedReservationCleanupJob.run();

    // Assert
    assertThat(redisTemplate.hasKey(key1)).isFalse();
    assertThat(redisTemplate.hasKey(key2)).isTrue();
  }
}
