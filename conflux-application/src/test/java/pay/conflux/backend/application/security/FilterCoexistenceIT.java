package pay.conflux.backend.application.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pay.conflux.backend.identity.constant.IdentityRoutes;
import pay.conflux.backend.identity.dto.LoginRequest;
import pay.conflux.backend.identity.dto.RegisterMerchantRequest;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.enums.UserType;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;
import pay.conflux.backend.provisioning.constant.Environment;
import pay.conflux.backend.provisioning.entity.ApiKey;
import pay.conflux.backend.provisioning.entity.Business;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;
import pay.conflux.backend.provisioning.repository.BusinessRepository;

/**
 * Demonstrates that {@code JwtAuthorizationFilter} and {@code ApiKeyAuthFilter} coexist without
 * stepping on each other. Three scenarios:
 *
 * <ol>
 *   <li>JWT-only (admin SUPER) on {@code /api/v1/admin/me} → 200 (JWT filter resolved the admin
 *       principal).
 *   <li>API-key-only (merchant) on {@code /api/v1/admin/me} → 403 (API-key filter resolved a
 *       MERCHANT authority, which is not enough for {@code ADMIN_VIEWER}).
 *   <li>JWT (admin SUPER) <em>and</em> {@code X-API-Key} (merchant) on {@code /api/v1/admin/me} →
 *       200 (JWT wins by filter order; if the API-key filter had won, the request would 403).
 * </ol>
 *
 * <p>The opposite proof — that an API-key call with no JWT correctly resolves the merchant — is
 * already covered by {@code ApiKeyAuthFilterTest} at the filter level.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
@ActiveProfiles("test")
class FilterCoexistenceIT {

  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private AdminProfileRepository adminProfileRepository;
  @Autowired private BusinessRepository businessRepository;
  @Autowired private ApiKeyRepository apiKeyRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void jwtOnly_resolvesAdminAndReachesProtectedEndpoint() throws Exception {
    String jwt = seedAdminAndLogin("filtercoex-jwtonly-", AdminTier.SUPER);

    mockMvc
        .perform(get(IdentityRoutes.ADMIN_ME).header("Authorization", "Bearer " + jwt))
        .andExpect(status().isOk());
  }

  @Test
  void apiKeyOnly_resolvesMerchantSoAdminEndpointReturns403() throws Exception {
    String apiKey = seedMerchantAndMintApiKey("filtercoex-apikeyonly");

    mockMvc
        .perform(get(IdentityRoutes.ADMIN_ME).header("X-API-Key", apiKey))
        .andExpect(status().isForbidden());
  }

  @Test
  void bothHeadersPresent_jwtWinsOverApiKey() throws Exception {
    String adminJwt = seedAdminAndLogin("filtercoex-both-", AdminTier.SUPER);
    String merchantApiKey = seedMerchantAndMintApiKey("filtercoex-both-mk");

    // Admin's JWT carries SUPER → grants ADMIN_VIEWER. If the API-key filter had set MERCHANT
    // first, the admin endpoint would 403. 200 proves the JWT filter ran first and claimed it.
    mockMvc
        .perform(
            get(IdentityRoutes.ADMIN_ME)
                .header("Authorization", "Bearer " + adminJwt)
                .header("X-API-Key", merchantApiKey))
        .andExpect(status().isOk());
  }

  @Test
  void jwtShapedAuthorizationButGarbageSignature_returns401NeverFallsThroughToApiKey()
      throws Exception {
    String merchantApiKey = seedMerchantAndMintApiKey("filtercoex-malformed");
    // Three-segment JWT shape but signed with a different secret — JwtTokenService rejects.
    String tamperedJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.aW52YWxpZHNpZ25hdHVyZQ";

    mockMvc
        .perform(
            get(IdentityRoutes.ADMIN_ME)
                .header("Authorization", "Bearer " + tamperedJwt)
                .header("X-API-Key", merchantApiKey))
        .andExpect(status().isUnauthorized());
  }

  private String seedAdminAndLogin(String prefix, AdminTier tier) throws Exception {
    String identifier = prefix + UUID.randomUUID() + "@example.com";
    String password = "coex-password";
    User u = new User();
    u.setId(UUID.randomUUID());
    u.setIdentifier(identifier);
    u.setIdentifierType(IdentifierType.EMAIL);
    u.setPasswordHash(passwordEncoder.encode(password));
    u.setUserType(UserType.ADMIN);
    u.setStatus(UserStatus.ACTIVE);
    u.setMfaEnabled(false);
    userRepository.save(u);

    AdminProfile profile = new AdminProfile();
    profile.setId(UUID.randomUUID());
    profile.setUserId(u.getId());
    profile.setDepartment("Coex");
    profile.setEmployeeId("EMP-" + UUID.randomUUID());
    profile.setAdminTier(tier);
    adminProfileRepository.save(profile);

    return login(identifier, password);
  }

  /** Returns the plaintext API key value (caller never sees this after generation in prod). */
  private String seedMerchantAndMintApiKey(String prefix) throws Exception {
    String merchantEmail = prefix + UUID.randomUUID() + "@example.com";
    mockMvc
        .perform(
            post(IdentityRoutes.MERCHANT_REGISTER)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterMerchantRequest(merchantEmail, "coex-password", "Coex Mc"))))
        .andExpect(status().isCreated());

    UUID merchantUserId =
        userRepository
            .findByIdentifierAndIdentifierTypeAndDeletedFalse(merchantEmail, IdentifierType.EMAIL)
            .orElseThrow()
            .getId();

    Business business = new Business();
    business.setMerchantId(merchantUserId);
    business.setName("Coex Business");
    business.setDisplayName("Coex Business");
    businessRepository.save(business);

    String plaintext = "sp_test_" + UUID.randomUUID().toString().replace("-", "");
    ApiKey apiKey = new ApiKey();
    apiKey.setBusinessId(business.getId());
    apiKey.setKeyHash(sha256Hex(plaintext));
    apiKey.setKeyPrefix("sp_test_");
    apiKey.setLastFour(plaintext.substring(plaintext.length() - 4));
    apiKey.setEnvironment(Environment.TEST);
    apiKey.setRevoked(false);
    apiKeyRepository.save(apiKey);

    return plaintext;
  }

  private String login(String identifier, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(IdentityRoutes.AUTH_LOGIN)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(
                        objectMapper.writeValueAsString(new LoginRequest(identifier, password))))
            .andExpect(status().isOk())
            .andReturn();
    String token =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("authToken")
            .asText();
    assertThat(token).isNotBlank();
    return token;
  }

  private static String sha256Hex(String plaintext) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] digest = md.digest(plaintext.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(digest);
  }
}
