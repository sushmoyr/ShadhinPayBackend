package com.shadhinpay.quota.cache;

import com.shadhinpay.quota.config.QuotaConfig;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisQuotaCacheAdapter implements QuotaCachePort {

  private final StringRedisTemplate redisTemplate;
  private final RedisScript<Boolean> confirmReservationScript;
  private final QuotaConfig quotaConfig;

  private String getPendingKey(UUID merchantId, String period, String reservationId) {
    return String.format("quota:pending:%s:%s:%s", merchantId, period, reservationId);
  }

  private String getFinalKey(UUID merchantId, String period) {
    return String.format("quota:final:%s:%s", merchantId, period);
  }

  private String getPendingPattern(UUID merchantId, String period) {
    return String.format("quota:pending:%s:%s:*", merchantId, period);
  }

  @Override
  public String reservePending(UUID merchantId, String period) {
    String reservationId = UUID.randomUUID().toString();
    String key = getPendingKey(merchantId, period, reservationId);
    Boolean success =
        redisTemplate
            .opsForValue()
            .setIfAbsent(key, "1", Duration.ofSeconds(quotaConfig.getPendingTtlSeconds()));
    if (!Boolean.TRUE.equals(success)) {
      throw new IllegalStateException("Failed to claim pending reservation key: " + key);
    }
    return reservationId;
  }

  @Override
  public boolean releasePending(UUID merchantId, String period, String reservationId) {
    String key = getPendingKey(merchantId, period, reservationId);
    Boolean deleted = redisTemplate.delete(key);
    return Boolean.TRUE.equals(deleted);
  }

  @Override
  public boolean confirmReservation(UUID merchantId, String period, String reservationId) {
    String pendingKey = getPendingKey(merchantId, period, reservationId);
    String finalKey = getFinalKey(merchantId, period);

    Boolean result =
        redisTemplate.execute(
            confirmReservationScript,
            List.of(pendingKey, finalKey),
            String.valueOf(quotaConfig.getFinalTtlSeconds()));

    return Boolean.TRUE.equals(result);
  }

  @Override
  public int getFinalCount(UUID merchantId, String period) {
    String finalKey = getFinalKey(merchantId, period);
    String value = redisTemplate.opsForValue().get(finalKey);
    if (value == null) {
      return 0;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      log.warn("Invalid final count format for key {}: {}", finalKey, value);
      return 0;
    }
  }

  @Override
  public int countPending(UUID merchantId, String period) {
    String pattern = getPendingPattern(merchantId, period);
    Long count =
        redisTemplate.execute(
            (org.springframework.data.redis.core.RedisCallback<Long>)
                connection -> {
                  long c = 0;
                  try (Cursor<byte[]> cursor =
                      connection.scan(
                          ScanOptions.scanOptions().match(pattern).count(100).build())) {
                    while (cursor.hasNext()) {
                      cursor.next();
                      c++;
                    }
                  }
                  return c;
                });
    if (count == null) {
      return 0;
    }
    return Math.toIntExact(count);
  }
}
