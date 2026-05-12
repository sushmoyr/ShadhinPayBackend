package pay.conflux.backend.risk.engine;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pay.conflux.backend.risk.enums.BlacklistType;
import pay.conflux.backend.risk.repository.BlacklistEntryRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlacklistCache {

  private final BlacklistEntryRepository blacklistEntryRepository;
  private final StringRedisTemplate redisTemplate;

  @PostConstruct
  public void hydrate() {
    try {
      doHydrate();
    } catch (Exception e) {
      log.warn(
          "BlacklistCache startup hydration failed ({}); scheduledHydrate will retry every 5min",
          e.getMessage());
    }
  }

  @Scheduled(fixedRate = 300000)
  public void scheduledHydrate() {
    try {
      doHydrate();
    } catch (Exception e) {
      log.warn("BlacklistCache scheduled hydration failed: {}", e.getMessage());
    }
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
