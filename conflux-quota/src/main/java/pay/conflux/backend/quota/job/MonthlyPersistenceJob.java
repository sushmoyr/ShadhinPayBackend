package pay.conflux.backend.quota.job;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pay.conflux.backend.quota.repository.QuotaUsageRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyPersistenceJob {

  private final StringRedisTemplate redisTemplate;
  private final QuotaUsageRepository quotaUsageRepository;
  private final Clock clock;

  /**
   * Runs on the 1st of every month at 00:05 UTC. Scans for all quota:final:* keys of the previous
   * month and upserts them into the database.
   */
  @Scheduled(cron = "0 5 0 1 * *", zone = "UTC")
  public void run() {
    runForPeriod(YearMonth.now(clock).minusMonths(1).toString());
  }

  public void runForPeriod(String period) {
    log.info("Starting MonthlyPersistenceJob for period: {}", period);
    String matchPattern = String.format("quota:final:*:%s", period);

    List<String> keys = scanKeys(matchPattern);
    int processed = 0;
    for (String key : keys) {
      String[] parts = key.split(":");
      if (parts.length != 4) {
        continue;
      }
      try {
        UUID merchantId = UUID.fromString(parts[2]);
        String countStr = redisTemplate.opsForValue().get(key);
        if (countStr != null) {
          int count = Integer.parseInt(countStr);
          quotaUsageRepository.upsertUsage(merchantId, period, count);
          processed++;
        }
      } catch (IllegalArgumentException e) {
        log.warn("Failed to parse/persist key {} in MonthlyPersistenceJob", key, e);
      }
    }

    log.info(
        "Finished MonthlyPersistenceJob for period: {}. Processed {} keys.", period, processed);
  }

  private List<String> scanKeys(String pattern) {
    List<String> keys = new ArrayList<>();
    try {
      redisTemplate.execute(
          (RedisCallback<Void>)
              connection -> {
                try (Cursor<byte[]> cursor =
                    connection.scan(ScanOptions.scanOptions().match(pattern).count(100).build())) {
                  while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                  }
                }
                return null;
              });
    } catch (Exception e) {
      log.error("Failed to scan keys in MonthlyPersistenceJob", e);
    }
    return keys;
  }
}
