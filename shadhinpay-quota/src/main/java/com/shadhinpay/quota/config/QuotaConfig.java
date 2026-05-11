package com.shadhinpay.quota.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class QuotaConfig {

  @Value("${shadhinpay.quota.free-per-month:10}")
  private int freeQuotaPerMonth;

  @Value("${shadhinpay.quota.pending-ttl-seconds:1800}")
  private long pendingTtlSeconds;

  @Value("${shadhinpay.quota.final-ttl-seconds:3024000}")
  private long finalTtlSeconds;

  public int getFreeQuotaPerMonth() {
    return freeQuotaPerMonth;
  }

  public long getPendingTtlSeconds() {
    return pendingTtlSeconds;
  }

  public long getFinalTtlSeconds() {
    return finalTtlSeconds;
  }

  @Bean
  public RedisScript<Boolean> confirmReservationScript() {
    String script =
        "if redis.call('DEL', KEYS[1]) == 1 then\n"
            + "    redis.call('INCR', KEYS[2])\n"
            + "    redis.call('EXPIRE', KEYS[2], ARGV[1])\n"
            + "    return true\n"
            + "else\n"
            + "    return false\n"
            + "end";
    return RedisScript.of(script, Boolean.class);
  }
}
