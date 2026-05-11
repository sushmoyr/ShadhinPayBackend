package com.shadhinpay.adapters.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.shadhinpay.adapters.port.Vendor;
import com.shadhinpay.adapters.port.VendorCredentials;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
class RedisTokenServiceIntegrationTest {

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redisTemplate;

  private VendorAuthClient vendorAuthClient;
  private RedisTokenService redisTokenService;

  @BeforeAll
  static void startRedis() {
    RedisStandaloneConfiguration config =
        new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379));
    connectionFactory = new LettuceConnectionFactory(config);
    connectionFactory.afterPropertiesSet();

    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
  }

  @AfterAll
  static void stopRedis() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @BeforeEach
  void cleanRedisAndBuildService() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    vendorAuthClient = spy(new MockVendorAuthClient());
    redisTokenService = new RedisTokenService(redisTemplate, vendorAuthClient);
  }

  @Test
  void getToken_cachesTokenAndDoesNotCallAuthClientTwice() {
    VendorCredentials creds = new VendorCredentials(Map.of("k", "v"));

    String token1 = redisTokenService.getToken(Vendor.MOCK, creds);
    assertThat(token1).isEqualTo("mock-token");
    verify(vendorAuthClient, times(1)).authenticate(Vendor.MOCK, creds);

    String token2 = redisTokenService.getToken(Vendor.MOCK, creds);
    assertThat(token2).isEqualTo("mock-token");
    verify(vendorAuthClient, times(1)).authenticate(Vendor.MOCK, creds);
  }

  @Test
  void getToken_differentCredentialsProduceDifferentCacheKeys() {
    VendorCredentials creds1 = new VendorCredentials(Map.of("k", "v1"));
    VendorCredentials creds2 = new VendorCredentials(Map.of("k", "v2"));

    redisTokenService.getToken(Vendor.MOCK, creds1);
    redisTokenService.getToken(Vendor.MOCK, creds2);

    verify(vendorAuthClient, times(1)).authenticate(Vendor.MOCK, creds1);
    verify(vendorAuthClient, times(1)).authenticate(Vendor.MOCK, creds2);
  }

  @Test
  void getToken_distributedLockEnsuresSingleAuthCallUnderConcurrency() throws Exception {
    int numThreads = 10;
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    CountDownLatch readyGate = new CountDownLatch(numThreads);
    CountDownLatch startGate = new CountDownLatch(1);

    VendorCredentials creds = new VendorCredentials(Map.of("concurrent", "test"));
    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < numThreads; i++) {
      tasks.add(
          () -> {
            readyGate.countDown();
            startGate.await();
            return redisTokenService.getToken(Vendor.MOCK, creds);
          });
    }

    List<Future<String>> resultsFuture = new ArrayList<>();
    for (Callable<String> task : tasks) {
      resultsFuture.add(executor.submit(task));
    }

    // Wait for all threads to be parked at the start gate, then fire them simultaneously.
    readyGate.await(5, TimeUnit.SECONDS);
    startGate.countDown();

    for (Future<String> result : resultsFuture) {
      assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("mock-token");
    }

    verify(vendorAuthClient, times(1)).authenticate(Vendor.MOCK, creds);

    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);
  }
}
