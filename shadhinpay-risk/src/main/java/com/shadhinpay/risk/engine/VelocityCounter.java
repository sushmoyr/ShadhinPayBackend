package com.shadhinpay.risk.engine;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VelocityCounter {

  private final StringRedisTemplate redisTemplate;
  private Clock clock = Clock.systemUTC();

  public void setClock(Clock clock) {
    this.clock = clock;
  }

  public long incrementAndGet(UUID merchantId, VelocityDimension dim, long windowSize) {
    long epochSecond = Instant.now(clock).getEpochSecond();
    long windowSeconds = windowSize > 0 ? epochSecond / windowSize : epochSecond;
    String key = String.format("risk:velocity:%s:%s:%d", merchantId, dim, windowSeconds);

    Long value = redisTemplate.opsForValue().increment(key);
    if (value != null && value == 1L) {
      redisTemplate.expire(key, 2 * windowSize, TimeUnit.SECONDS);
    }
    return value != null ? value : 0L;
  }
}
