package pay.conflux.backend.adapters.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import pay.conflux.backend.adapters.error.MfsAdapterException;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;
import pay.conflux.backend.common.error.ErrorCode;

@Component
@Primary
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisTokenService implements TokenService {

  private static final Logger log = LoggerFactory.getLogger(RedisTokenService.class);
  private final StringRedisTemplate redisTemplate;
  private final VendorAuthClient vendorAuthClient;

  public RedisTokenService(StringRedisTemplate redisTemplate, VendorAuthClient vendorAuthClient) {
    this.redisTemplate = redisTemplate;
    this.vendorAuthClient = vendorAuthClient;
  }

  @Override
  public String getToken(Vendor v, VendorCredentials creds) {
    String credsHash = hashCredentials(creds);
    String cacheKey = "adapter:token:" + v.name() + ":" + credsHash;
    String lockKey = "adapter:token:lock:" + v.name() + ":" + credsHash;

    // 1. First check cache
    String token = getIfValid(cacheKey);
    if (token != null) {
      return token;
    }

    // 2. Cache miss or near expiration, acquire lock
    long startWait = System.currentTimeMillis();
    boolean locked = false;

    while (!locked && (System.currentTimeMillis() - startWait < 4000)) {
      locked =
          Boolean.TRUE.equals(
              redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(5)));

      if (locked) {
        try {
          // Double-check pattern
          token = getIfValid(cacheKey);
          if (token != null) {
            return token;
          }

          log.info(
              "Fetching new token for vendor: {}, correlationId: {}", v.name(), System.nanoTime());
          VendorAuthClient.AuthToken authToken = vendorAuthClient.authenticate(v, creds);

          long secondsToLive =
              Duration.between(Instant.now(), authToken.expiresAt()).getSeconds() - 60;
          if (secondsToLive <= 0) {
            secondsToLive = 1; // At least 1 second to ensure it can be read immediately
          }

          redisTemplate
              .opsForValue()
              .set(cacheKey, authToken.token(), Duration.ofSeconds(secondsToLive));
          return authToken.token();
        } finally {
          redisTemplate.delete(lockKey);
        }
      } else {
        // Did not acquire lock, wait and check cache again
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new MfsAdapterException(ErrorCode.VENDOR_DOWN, "Token fetch interrupted", e);
        }
        token = getIfValid(cacheKey);
        if (token != null) {
          return token;
        }
      }
    }

    throw new MfsAdapterException(ErrorCode.VENDOR_DOWN, "Token fetch contention");
  }

  private String getIfValid(String cacheKey) {
    Long expire = redisTemplate.getExpire(cacheKey);
    // If key exists and TTL is reasonably positive (we already subtract 60s when saving)
    // Spring Data Redis returns -2 if key does not exist, -1 if no expiry
    if (expire != null && expire > 0) {
      return redisTemplate.opsForValue().get(cacheKey);
    }
    return null;
  }

  private String hashCredentials(VendorCredentials creds) {
    try {
      Map<String, String> sortedCreds = new TreeMap<>(creds.values());
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, String> entry : sortedCreds.entrySet()) {
        sb.append(entry.getKey()).append('=').append(entry.getValue()).append(';');
      }
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder(2 * hash.length);
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not found", e);
    }
  }
}
