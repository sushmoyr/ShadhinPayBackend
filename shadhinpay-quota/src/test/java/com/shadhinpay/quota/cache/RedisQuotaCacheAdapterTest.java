package com.shadhinpay.quota.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.shadhinpay.quota.config.QuotaConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    classes = {RedisAutoConfiguration.class, RedisQuotaCacheAdapter.class, QuotaConfig.class})
@Testcontainers(disabledWithoutDocker = true)
class RedisQuotaCacheAdapterTest {

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired private RedisQuotaCacheAdapter cacheAdapter;

  @Test
  void testReserveConfirmRelease() {
    UUID merchantId = UUID.randomUUID();
    String period = "2025-01";

    // Reserve
    String res1 = cacheAdapter.reservePending(merchantId, period);
    String res2 = cacheAdapter.reservePending(merchantId, period);

    assertThat(cacheAdapter.countPending(merchantId, period)).isEqualTo(2);
    assertThat(cacheAdapter.getFinalCount(merchantId, period)).isEqualTo(0);

    // Confirm 1
    boolean confirmed = cacheAdapter.confirmReservation(merchantId, period, res1);
    assertThat(confirmed).isTrue();

    assertThat(cacheAdapter.countPending(merchantId, period)).isEqualTo(1);
    assertThat(cacheAdapter.getFinalCount(merchantId, period)).isEqualTo(1);

    // Release 2
    boolean released = cacheAdapter.releasePending(merchantId, period, res2);
    assertThat(released).isTrue();

    assertThat(cacheAdapter.countPending(merchantId, period)).isEqualTo(0);
    assertThat(cacheAdapter.getFinalCount(merchantId, period)).isEqualTo(1);
  }

  @Test
  void testConfirmNonExistent() {
    UUID merchantId = UUID.randomUUID();
    String period = "2025-01";
    boolean confirmed =
        cacheAdapter.confirmReservation(merchantId, period, UUID.randomUUID().toString());
    assertThat(confirmed).isFalse();
    assertThat(cacheAdapter.getFinalCount(merchantId, period)).isEqualTo(0);
  }
}
