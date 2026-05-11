package com.shadhinpay.quota;

import static org.assertj.core.api.Assertions.assertThat;

import com.shadhinpay.quota.cache.QuotaCachePort;
import com.shadhinpay.quota.usecase.ConfirmQuotaUseCase;
import com.shadhinpay.quota.usecase.GetUsageUseCase;
import com.shadhinpay.quota.usecase.QuotaReservation;
import com.shadhinpay.quota.usecase.QuotaUsageView;
import com.shadhinpay.quota.usecase.ReleaseQuotaUseCase;
import com.shadhinpay.quota.usecase.ReserveQuotaUseCase;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class QuotaIntegrationTest {

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

  @Autowired private ReserveQuotaUseCase reserveUseCase;

  @Autowired private ConfirmQuotaUseCase confirmUseCase;

  @Autowired private ReleaseQuotaUseCase releaseUseCase;

  @Autowired private GetUsageUseCase getUsageUseCase;

  @Autowired private QuotaCachePort cachePort;

  @Test
  void testReserveConfirmReleaseSequence() {
    UUID merchantId = UUID.randomUUID();
    String period = YearMonth.now(ZoneOffset.UTC).toString();

    List<QuotaReservation> reservations = new ArrayList<>();
    for (int i = 0; i < 15; i++) {
      reservations.add(reserveUseCase.execute(merchantId));
    }

    // Confirm 8
    for (int i = 0; i < 8; i++) {
      confirmUseCase.execute(merchantId, reservations.get(i).reservationId());
    }

    // Release 7
    for (int i = 8; i < 15; i++) {
      releaseUseCase.execute(merchantId, reservations.get(i).reservationId());
    }

    int finalCount = cachePort.getFinalCount(merchantId, period);
    int pendingCount = cachePort.countPending(merchantId, period);

    assertThat(finalCount).isEqualTo(8);
    assertThat(pendingCount).isEqualTo(0);

    QuotaUsageView usage = getUsageUseCase.execute(merchantId, period);
    assertThat(usage.usedCount()).isEqualTo(8);
    assertThat(usage.freeRemaining()).isEqualTo(2);
  }

  @Test
  void testConcurrency() throws InterruptedException {
    UUID merchantId = UUID.randomUUID();
    String period = YearMonth.now(ZoneOffset.UTC).toString();
    int threads = 100;
    CountDownLatch latch = new CountDownLatch(threads);
    ExecutorService executor = Executors.newFixedThreadPool(16);

    for (int i = 0; i < threads; i++) {
      executor.submit(
          () -> {
            try {
              QuotaReservation res = reserveUseCase.execute(merchantId);
              confirmUseCase.execute(merchantId, res.reservationId());
            } finally {
              latch.countDown();
            }
          });
    }

    latch.await();
    executor.shutdown();

    int finalCount = cachePort.getFinalCount(merchantId, period);
    assertThat(finalCount).isEqualTo(100);
  }
}
