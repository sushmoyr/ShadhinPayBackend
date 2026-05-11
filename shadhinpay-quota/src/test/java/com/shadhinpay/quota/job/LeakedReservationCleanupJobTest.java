package com.shadhinpay.quota.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shadhinpay.quota.config.QuotaConfig;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

class LeakedReservationCleanupJobTest {

  private StringRedisTemplate redisTemplate;
  private QuotaConfig quotaConfig;
  private LeakedReservationCleanupJob job;

  @BeforeEach
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    quotaConfig = mock(QuotaConfig.class);
    when(quotaConfig.getPendingTtlSeconds()).thenReturn(1800L);

    job = new LeakedReservationCleanupJob(redisTemplate, quotaConfig);
  }

  @SuppressWarnings("unchecked")
  private void stubScan(List<String> keys) {
    Cursor<byte[]> cursor = mock(Cursor.class);
    when(cursor.hasNext())
        .thenAnswer(
            new org.mockito.stubbing.Answer<Boolean>() {
              int idx = 0;

              @Override
              public Boolean answer(org.mockito.invocation.InvocationOnMock invocation) {
                return idx < keys.size();
              }
            });
    when(cursor.next())
        .thenAnswer(
            new org.mockito.stubbing.Answer<byte[]>() {
              int idx = 0;

              @Override
              public byte[] answer(org.mockito.invocation.InvocationOnMock invocation) {
                return keys.get(idx++).getBytes();
              }
            });

    RedisConnection connection = mock(RedisConnection.class);
    when(connection.scan(any(ScanOptions.class))).thenReturn(cursor);

    doAnswer(
            invocation -> {
              RedisCallback<?> cb = invocation.getArgument(0);
              return cb.doInRedis(connection);
            })
        .when(redisTemplate)
        .execute(any(RedisCallback.class));
  }

  @Test
  void run_deletesKeysWithoutExpiry() {
    String key = "quota:pending:merch:2026-05:res1";
    stubScan(List.of(key));
    when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(-1L);

    job.run();

    verify(redisTemplate).delete(key);
  }

  @Test
  void run_deletesKeysWithTooLargeExpiry() {
    String key = "quota:pending:merch:2026-05:res1";
    stubScan(List.of(key));
    when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(3600L);

    job.run();

    verify(redisTemplate).delete(key);
  }

  @Test
  void run_ignoresHealthyKeys() {
    String key = "quota:pending:merch:2026-05:res1";
    stubScan(List.of(key));
    when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(1700L);

    job.run();

    verify(redisTemplate, never()).delete(key);
  }

  @Test
  void run_handlesEmptyScan() {
    stubScan(List.of());

    job.run();

    verify(redisTemplate, never()).delete(any(String.class));
  }
}
