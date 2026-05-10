package com.shadhinpay.risk.engine;

import com.shadhinpay.risk.entity.BlacklistType;
import com.shadhinpay.risk.repository.BlacklistEntryRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlacklistCache {

  private final BlacklistEntryRepository blacklistEntryRepository;
  private final StringRedisTemplate redisTemplate;

  @PostConstruct
  public void hydrate() {
    doHydrate();
  }

  @Scheduled(fixedRate = 300000)
  public void scheduledHydrate() {
    doHydrate();
  }

  private void doHydrate() {
    Instant now = Instant.now();
    for (BlacklistType type : BlacklistType.values()) {
      String key = getRedisKey(type);
      redisTemplate.delete(key);
      blacklistEntryRepository
          .findAllActiveByType(type, now)
          .forEach(
              entry -> {
                redisTemplate.opsForSet().add(key, entry.getValue());
              });
    }
  }

  public boolean isBlacklisted(BlacklistType type, String value) {
    if (value == null) {
      return false;
    }
    String key = getRedisKey(type);
    Boolean isMember = redisTemplate.opsForSet().isMember(key, value);
    return Boolean.TRUE.equals(isMember);
  }

  public void add(BlacklistType type, String value) {
    if (value != null) {
      redisTemplate.opsForSet().add(getRedisKey(type), value);
    }
  }

  public void remove(BlacklistType type, String value) {
    if (value != null) {
      redisTemplate.opsForSet().remove(getRedisKey(type), value);
    }
  }

  private String getRedisKey(BlacklistType type) {
    return "risk:blacklist:" + type.name();
  }
}
