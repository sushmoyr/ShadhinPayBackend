package com.shadhinpay.quota.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shadhinpay.quota.config.QuotaConfig;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisQuotaCacheAdapterMockTest {

  private StringRedisTemplate redisTemplate;
  private RedisScript<Boolean> script;
  private QuotaConfig quotaConfig;
  private RedisQuotaCacheAdapter adapter;
  private ValueOperations<String, String> valueOps;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    script = mock(RedisScript.class);
    valueOps = mock(ValueOperations.class);
    quotaConfig = mock(QuotaConfig.class);
    when(quotaConfig.getPendingTtlSeconds()).thenReturn(1800L);
    when(quotaConfig.getFinalTtlSeconds()).thenReturn(3024000L);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    adapter = new RedisQuotaCacheAdapter(redisTemplate, script, quotaConfig);
  }

  @Test
  void testReservePending() {
    UUID merchantId = UUID.randomUUID();
    String period = "2025-01";
    when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

    String resId = adapter.reservePending(merchantId, period);

    assertThat(resId).isNotNull();
    verify(valueOps).setIfAbsent(anyString(), eq("1"), any(Duration.class));
  }

  @Test
  void testReservePending_SetIfAbsentFalse_throws() {
    UUID merchantId = UUID.randomUUID();
    String period = "2025-01";
    when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

    assertThatThrownBy(() -> adapter.reservePending(merchantId, period))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to claim pending reservation key");
  }

  @Test
  void testReservePending_SetIfAbsentNull_throws() {
    UUID merchantId = UUID.randomUUID();
    String period = "2025-01";
    when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(null);

    assertThatThrownBy(() -> adapter.reservePending(merchantId, period))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void testReleasePending() {
    UUID merchantId = UUID.randomUUID();
    String period = "2025-01";
    String resId = UUID.randomUUID().toString();
    when(redisTemplate.delete(anyString())).thenReturn(true);

    boolean result = adapter.releasePending(merchantId, period, resId);

    assertThat(result).isTrue();
    verify(redisTemplate).delete(anyString());
  }

  @Test
  void testReleasePending_NotFound() {
    UUID merchantId = UUID.randomUUID();
    String period = "2025-01";
    String resId = UUID.randomUUID().toString();
    when(redisTemplate.delete(anyString())).thenReturn(false);

    boolean result = adapter.releasePending(merchantId, period, resId);

    assertThat(result).isFalse();
  }

  @Test
  void testConfirmReservation() {
    UUID merchantId = UUID.randomUUID();
    String period = "2025-01";
    String resId = UUID.randomUUID().toString();
    when(redisTemplate.execute(eq(script), any(List.class), anyString())).thenReturn(true);

    boolean result = adapter.confirmReservation(merchantId, period, resId);

    assertThat(result).isTrue();
    verify(redisTemplate).execute(eq(script), any(List.class), anyString());
  }

  @Test
  void testConfirmReservation_PendingMissing() {
    UUID merchantId = UUID.randomUUID();
    String period = "2025-01";
    String resId = UUID.randomUUID().toString();
    when(redisTemplate.execute(eq(script), any(List.class), anyString())).thenReturn(false);

    assertThat(adapter.confirmReservation(merchantId, period, resId)).isFalse();
  }

  @Test
  void testGetFinalCount() {
    UUID merchantId = UUID.randomUUID();
    String period = "2025-01";
    when(valueOps.get(anyString())).thenReturn("15");

    int count = adapter.getFinalCount(merchantId, period);

    assertThat(count).isEqualTo(15);
  }

  @Test
  void testGetFinalCount_Empty() {
    UUID merchantId = UUID.randomUUID();
    String period = "2025-01";
    when(valueOps.get(anyString())).thenReturn(null);

    int count = adapter.getFinalCount(merchantId, period);

    assertThat(count).isEqualTo(0);
  }

  @Test
  void testGetFinalCount_Malformed() {
    UUID merchantId = UUID.randomUUID();
    String period = "2025-01";
    when(valueOps.get(anyString())).thenReturn("not-a-number");

    int count = adapter.getFinalCount(merchantId, period);

    assertThat(count).isEqualTo(0);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testCountPending() {
    UUID merchantId = UUID.randomUUID();
    String period = "2025-01";
    when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(3L);

    int count = adapter.countPending(merchantId, period);
    assertThat(count).isEqualTo(3);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testCountPending_NullResult() {
    UUID merchantId = UUID.randomUUID();
    String period = "2025-01";
    when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);

    int count = adapter.countPending(merchantId, period);
    assertThat(count).isEqualTo(0);
  }
}
