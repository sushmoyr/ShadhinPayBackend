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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Fast in-process unit tests for {@code RedisTokenService} that mock out {@code
 * StringRedisTemplate} with a {@code ConcurrentHashMap}. These exercise the cache-hit, cache-miss,
 * credentials-hash, and lock-acquisition code paths so coverage holds even when Docker (and
 * therefore the Testcontainers-backed {@code RedisTokenServiceIntegrationTest}) is unavailable.
 */
@ExtendWith(MockitoExtension.class)
class RedisTokenServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private VendorAuthClient vendorAuthClient;
  private RedisTokenService redisTokenService;

  private ConcurrentHashMap<String, String> cache;

  @BeforeEach
  void setUp() {
    cache = new ConcurrentHashMap<>();
    vendorAuthClient = spy(new MockVendorAuthClient());
    redisTokenService = new RedisTokenService(redisTemplate, vendorAuthClient);

    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    lenient()
        .when(redisTemplate.getExpire(anyString()))
        .thenAnswer(inv -> cache.containsKey((String) inv.getArgument(0)) ? 100L : -2L);
    lenient()
        .when(valueOperations.get(anyString()))
        .thenAnswer(inv -> cache.get((String) inv.getArgument(0)));
    lenient()
        .doAnswer(
            inv -> {
              cache.put(inv.getArgument(0), inv.getArgument(1));
              return null;
            })
        .when(valueOperations)
        .set(anyString(), anyString(), any(Duration.class));
    lenient()
        .when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .thenAnswer(inv -> cache.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);
    lenient()
        .when(redisTemplate.delete(anyString()))
        .thenAnswer(
            inv -> {
              cache.remove((String) inv.getArgument(0));
              return true;
            });
  }

  @Test
  void getToken_cachesTokenAndDoesNotCallAuthClientTwice() {
    VendorCredentials creds = new VendorCredentials(Map.of("k", "v"));

    String token1 = redisTokenService.getToken(Vendor.MOCK, creds);
    assertThat(token1).isEqualTo("mock-token");

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
  void getToken_emptyCredentials_stillProducesStableHash() {
    VendorCredentials creds = new VendorCredentials(Map.of());

    String token = redisTokenService.getToken(Vendor.MOCK, creds);

    assertThat(token).isEqualTo("mock-token");
  }
}
