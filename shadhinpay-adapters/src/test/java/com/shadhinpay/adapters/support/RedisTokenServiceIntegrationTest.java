package com.shadhinpay.adapters.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.shadhinpay.adapters.port.Vendor;
import com.shadhinpay.adapters.port.VendorCredentials;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisTokenServiceIntegrationTest {

  @Mock private StringRedisTemplate redisTemplate;

  @Mock private ValueOperations<String, String> valueOperations;

  private VendorAuthClient vendorAuthClient;

  private RedisTokenService redisTokenService;

  private ConcurrentHashMap<String, String> cache;
  private ConcurrentHashMap<String, Long> expires;

  @BeforeEach
  void setUp() {
    cache = new ConcurrentHashMap<>();
    expires = new ConcurrentHashMap<>();

    vendorAuthClient = spy(new MockVendorAuthClient());
    redisTokenService = new RedisTokenService(redisTemplate, vendorAuthClient);

    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // Mock getExpire
    lenient()
        .when(redisTemplate.getExpire(anyString()))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              if (!cache.containsKey(key)) return -2L;
              return 100L; // Always valid if present
            });

    // Mock get
    lenient()
        .when(valueOperations.get(anyString()))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              return cache.get(key);
            });

    // Mock set
    lenient()
        .doAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              String val = invocation.getArgument(1);
              cache.put(key, val);
              return null;
            })
        .when(valueOperations)
        .set(anyString(), anyString(), any(Duration.class));

    // Mock setIfAbsent
    lenient()
        .when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              String val = invocation.getArgument(1);
              return cache.putIfAbsent(key, val) == null;
            });

    // Mock delete
    lenient()
        .when(redisTemplate.delete(anyString()))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              cache.remove(key);
              return true;
            });
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

    verify(vendorAuthClient, times(2)).authenticate(any(), any());
  }

  @Test
  void getToken_distributedLockEnsuresSingleAuthCallUnderConcurrency() throws Exception {
    int numThreads = 10;
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    List<Callable<String>> tasks = new ArrayList<>();

    VendorCredentials creds = new VendorCredentials(Map.of("concurrent", "test"));

    for (int i = 0; i < numThreads; i++) {
      tasks.add(() -> redisTokenService.getToken(Vendor.MOCK, creds));
    }

    List<Future<String>> results = executor.invokeAll(tasks);

    for (Future<String> result : results) {
      assertThat(result.get()).isEqualTo("mock-token");
    }

    verify(vendorAuthClient, times(1)).authenticate(Vendor.MOCK, creds);

    executor.shutdown();
  }
}
