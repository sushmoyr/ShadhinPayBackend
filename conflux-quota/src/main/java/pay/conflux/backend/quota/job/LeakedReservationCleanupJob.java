package pay.conflux.backend.quota.job;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pay.conflux.backend.quota.config.QuotaConfig;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeakedReservationCleanupJob {

  private final StringRedisTemplate redisTemplate;
  private final QuotaConfig quotaConfig;

  /**
   * Runs every 10 minutes. Scans for all quota:pending:* keys and deletes those with no TTL or
   * unexpectedly large TTL.
   */
  @Scheduled(fixedRate = 600000)
  public void run() {
    log.info("Starting LeakedReservationCleanupJob");
    int deleted = 0;

    List<String> keys = scanKeys();
    long maxExpectedTtl = quotaConfig.getPendingTtlSeconds();
    for (String key : keys) {
      Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);

      if (ttl != null && (ttl == -1 || ttl > maxExpectedTtl)) {
        log.warn("Found anomalous pending reservation key (TTL: {}). Deleting: {}", ttl, key);
        redisTemplate.delete(key);
        deleted++;
      }
    }

    log.info("Finished LeakedReservationCleanupJob. Deleted {} leaked reservations.", deleted);
  }

  private List<String> scanKeys() {
    List<String> keys = new ArrayList<>();
    try {
      redisTemplate.execute(
          (RedisCallback<Void>)
              connection -> {
                try (Cursor<byte[]> cursor =
                    connection.scan(
                        ScanOptions.scanOptions().match("quota:pending:*").count(100).build())) {
                  while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                  }
                }
                return null;
              });
    } catch (Exception e) {
      log.error("Failed to scan keys in LeakedReservationCleanupJob", e);
    }
    return keys;
  }
}
