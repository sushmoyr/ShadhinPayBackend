package pay.conflux.backend.identity.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.common.error.UnauthorizedException;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.UserType;

class JwtTokenServiceTest {

  private static final String VALID_SECRET_B64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

  @Test
  void verify_failsFastOnBlankSecret() {
    JwtTokenService svc = new JwtTokenService("", 60);
    assertThatThrownBy(svc::verify)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not configured");
  }

  @Test
  void verify_failsFastOnShortSecret() {
    String shortB64 = Base64.getEncoder().encodeToString(new byte[10]);
    JwtTokenService svc = new JwtTokenService(shortB64, 60);
    assertThatThrownBy(svc::verify)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  void verify_failsFastOnNonBase64Secret() {
    JwtTokenService svc = new JwtTokenService("not-base64-!!!@@@", 60);
    assertThatThrownBy(svc::verify)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Base64");
  }

  @Test
  void issueParse_merchantRoundTripWithoutTierClaim() {
    JwtTokenService svc = newReadyService();
    User user = newUser(UUID.randomUUID(), UserType.MERCHANT);

    String token = svc.issue(user, null);
    JwtClaims claims = svc.parse(token);

    assertThat(claims.userId()).isEqualTo(user.getId());
    assertThat(claims.userType()).isEqualTo(UserType.MERCHANT);
    assertThat(claims.tier()).isNull();
  }

  @Test
  void issueParse_adminSuperRoundTripWithTierClaim() {
    JwtTokenService svc = newReadyService();
    User user = newUser(UUID.randomUUID(), UserType.ADMIN);
    AdminProfile profile = newProfile(user.getId(), AdminTier.SUPER);

    String token = svc.issue(user, profile);
    JwtClaims claims = svc.parse(token);

    assertThat(claims.userType()).isEqualTo(UserType.ADMIN);
    assertThat(claims.tier()).isEqualTo(AdminTier.SUPER);
  }

  @Test
  void issue_adminWithoutProfileThrowsIllegalState() {
    JwtTokenService svc = newReadyService();
    User user = newUser(UUID.randomUUID(), UserType.ADMIN);

    assertThatThrownBy(() -> svc.issue(user, null)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void parse_expiredTokenRejected() {
    JwtTokenService svc = newReadyService();
    SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(VALID_SECRET_B64));
    long past = System.currentTimeMillis() - 120_000L;
    String expired =
        Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .claim("userType", "MERCHANT")
            .issuedAt(new Date(past))
            .expiration(new Date(past + 1_000L))
            .signWith(key, Jwts.SIG.HS256)
            .compact();

    assertThatThrownBy(() -> svc.parse(expired))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("expired");
  }

  @Test
  void parse_tamperedSignatureRejected() {
    JwtTokenService svc = newReadyService();
    SecretKey wrongKey = Keys.hmacShaKeyFor(new byte[32]);
    String tampered =
        Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .claim("userType", "MERCHANT")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60_000L))
            .signWith(wrongKey, Jwts.SIG.HS256)
            .compact();

    assertThatThrownBy(() -> svc.parse(tampered))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("invalid");
  }

  @Test
  void parse_malformedTokenRejected() {
    JwtTokenService svc = newReadyService();
    assertThatThrownBy(() -> svc.parse("not.a.jwt"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("invalid");
  }

  @Test
  void parse_nullTokenRejected() {
    JwtTokenService svc = newReadyService();
    assertThatThrownBy(() -> svc.parse(null))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("missing");
  }

  private static JwtTokenService newReadyService() {
    JwtTokenService svc = new JwtTokenService(VALID_SECRET_B64, 60);
    svc.verify();
    return svc;
  }

  private static User newUser(UUID id, UserType type) {
    User u = new User();
    u.setId(id);
    u.setUserType(type);
    return u;
  }

  private static AdminProfile newProfile(UUID userId, AdminTier tier) {
    AdminProfile p = new AdminProfile();
    p.setId(UUID.randomUUID());
    p.setUserId(userId);
    p.setAdminTier(tier);
    return p;
  }
}
