package pay.conflux.backend.identity.support;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.UserType;

/**
 * Issues and verifies HS256 JWTs for the identity module. Replaces the Wave A stub-HMAC token path
 * in {@code AuthenticateUserUseCaseImpl}.
 *
 * <p>The secret is read from {@code conflux.identity.jwt.secret} as a Base64-encoded value; after
 * decoding it must be at least 32 bytes (HS256's minimum key size). Startup fails fast otherwise so
 * a misconfigured environment can't ship a weak token. See {@code
 * DOCS/features/identity/TECH_SPEC.md §4.3} for the claim shape consumed by the Wave D 1c filter.
 */
@Slf4j
@Component
public class JwtTokenService {

  static final String CLAIM_USER_TYPE = "userType";
  static final String CLAIM_TIER = "tier";
  private static final int MIN_SECRET_BYTES = 32;

  private final String configuredSecret;
  private final long expiryMinutes;
  private SecretKey signingKey;

  public JwtTokenService(
      @Value("${conflux.identity.jwt.secret}") String secret,
      @Value("${conflux.identity.jwt.expiry-minutes:60}") long expiryMinutes) {
    this.configuredSecret = secret;
    this.expiryMinutes = expiryMinutes;
  }

  @PostConstruct
  void verify() {
    if (configuredSecret == null || configuredSecret.isBlank()) {
      throw new IllegalStateException("conflux.identity.jwt.secret is not configured");
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(configuredSecret);
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException("conflux.identity.jwt.secret must be Base64-encoded", ex);
    }
    if (decoded.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "conflux.identity.jwt.secret must decode to at least "
              + MIN_SECRET_BYTES
              + " bytes (got "
              + decoded.length
              + ")");
    }
    this.signingKey = Keys.hmacShaKeyFor(decoded);
  }

  public String issue(User user, AdminProfile adminProfileOrNull) {
    if (user.getUserType() == UserType.ADMIN && adminProfileOrNull == null) {
      throw new IllegalStateException(
          "AdminProfile is required to issue a JWT for ADMIN user " + user.getId());
    }
    long nowMillis = System.currentTimeMillis();
    Date issuedAt = new Date(nowMillis);
    Date expiresAt = new Date(nowMillis + expiryMinutes * 60_000L);

    var builder =
        Jwts.builder()
            .subject(user.getId().toString())
            .claim(CLAIM_USER_TYPE, user.getUserType().name())
            .issuedAt(issuedAt)
            .expiration(expiresAt);
    if (user.getUserType() == UserType.ADMIN) {
      builder.claim(CLAIM_TIER, adminProfileOrNull.getAdminTier().name());
    }
    return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
  }

  public JwtClaims parse(String token) {
    if (token == null || token.isBlank()) {
      throw new UnauthorizedException("JWT is missing");
    }
    try {
      Claims claims =
          Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
      UUID userId = UUID.fromString(claims.getSubject());
      UserType userType = UserType.valueOf(claims.get(CLAIM_USER_TYPE, String.class));
      AdminTier tier = null;
      if (userType == UserType.ADMIN) {
        String tierClaim = claims.get(CLAIM_TIER, String.class);
        if (tierClaim == null) {
          throw new UnauthorizedException("JWT is missing tier claim for ADMIN user");
        }
        tier = AdminTier.valueOf(tierClaim);
      }
      return new JwtClaims(userId, userType, tier);
    } catch (ExpiredJwtException ex) {
      log.debug("JWT rejected: expired");
      throw new UnauthorizedException("JWT is expired");
    } catch (JwtException | IllegalArgumentException ex) {
      log.debug("JWT rejected: {}", ex.getClass().getSimpleName());
      throw new UnauthorizedException("JWT is invalid");
    }
  }

  /** Visible for unit tests that need to assert behavior across the encoding boundary. */
  static String encodeSecret(byte[] raw) {
    return Base64.getEncoder().encodeToString(raw);
  }

  /** Visible for unit tests that need to construct a raw secret deterministically. */
  static byte[] decodeSecret(String b64) {
    return Base64.getDecoder().decode(b64.getBytes(StandardCharsets.UTF_8));
  }
}
