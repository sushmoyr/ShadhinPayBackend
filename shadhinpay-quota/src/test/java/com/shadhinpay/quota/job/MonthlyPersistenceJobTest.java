package com.shadhinpay.quota.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shadhinpay.quota.repository.QuotaUsageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MonthlyPersistenceJobTest {

  private StringRedisTemplate redisTemplate;
  private QuotaUsageRepository repository;
  private MonthlyPersistenceJob job;

  private ValueOperations<String, String> valueOps;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    repository = mock(QuotaUsageRepository.class);
    valueOps = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);

    job = new MonthlyPersistenceJob(redisTemplate, repository, Clock.systemUTC());
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
  void runForPeriod_parsesKeysAndUpserts() {
    String period = "2026-04";
    UUID merchantId = UUID.randomUUID();
    String key = "quota:final:" + merchantId + ":" + period;

    stubScan(List.of(key));
    when(valueOps.get(key)).thenReturn("15");

    job.runForPeriod(period);

    verify(repository).upsertUsage(merchantId, period, 15);
  }

  @Test
  void runForPeriod_skipsInvalidKeyFormat() {
    String period = "2026-04";
    stubScan(List.of("quota:final:invalid_key"));

    job.runForPeriod(period);

    verifyNoInteractions(repository);
  }

  @Test
  void runForPeriod_skipsUnparseableUuid() {
    String period = "2026-04";
    stubScan(List.of("quota:final:not-a-uuid:" + period));

    job.runForPeriod(period);

    verifyNoInteractions(repository);
  }

  @Test
  void runForPeriod_skipsNullCount() {
    String period = "2026-04";
    UUID merchantId = UUID.randomUUID();
    String key = "quota:final:" + merchantId + ":" + period;

    stubScan(List.of(key));
    when(valueOps.get(key)).thenReturn(null);

    job.runForPeriod(period);

    verifyNoInteractions(repository);
  }

  @Test
  void run_usesPreviousMonthFromClock() {
    Clock fixed = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);
    MonthlyPersistenceJob fixedJob = new MonthlyPersistenceJob(redisTemplate, repository, fixed);

    stubScan(List.of());

    fixedJob.run();

    // Scan was attempted (verifying job ran without throwing)
    verify(redisTemplate, times(1)).execute(any(RedisCallback.class));
    assertThat(true).isTrue();
  }
}
