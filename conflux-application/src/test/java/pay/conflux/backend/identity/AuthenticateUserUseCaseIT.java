package pay.conflux.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

/**
 * Verifies the Wave D 1b JWT-issuing login: merchant tokens carry no tier claim while admin tokens
 * carry their {@code adminTier} string. Exercised through {@code POST /api/v1/auth/login} via
 * MockMvc.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
@ActiveProfiles("test")
class AuthenticateUserUseCaseIT {

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
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void merchantLogin_jwtHasUserTypeMerchantAndNoTierClaim() throws Exception {
    String identifier = "auth-it-merchant@example.com";
    String password = "password123";

    mockMvc
        .perform(
            post(IdentityRoutes.MERCHANT_REGISTER)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterMerchantRequest(identifier, password, "AuthIT Merchant"))))
        .andExpect(status().isCreated());

    MvcResult result =
        mockMvc
            .perform(
                post(IdentityRoutes.AUTH_LOGIN)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(
                        objectMapper.writeValueAsString(new LoginRequest(identifier, password))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.authToken").exists())
            .andReturn();

    String token =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("authToken")
            .asText();

    JsonNode payload = decodeJwtPayload(token);
    assertThat(payload.get("userType").asText()).isEqualTo("MERCHANT");
    assertThat(payload.has("tier")).isFalse();
  }

  @Test
  void adminSuperLogin_jwtHasUserTypeAdminAndSuperTierClaim() throws Exception {
    String identifier = "auth-it-super@example.com";
    String password = "super-password";
    UUID userId = seedAdmin(identifier, password, AdminTier.SUPER);

    MvcResult result =
        mockMvc
            .perform(
                post(IdentityRoutes.AUTH_LOGIN)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(
                        objectMapper.writeValueAsString(new LoginRequest(identifier, password))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userType").value("ADMIN"))
            .andReturn();

    String token =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("authToken")
            .asText();

    JsonNode payload = decodeJwtPayload(token);
    assertThat(payload.get("userType").asText()).isEqualTo("ADMIN");
    assertThat(payload.get("tier").asText()).isEqualTo("SUPER");
    assertThat(UUID.fromString(payload.get("sub").asText())).isEqualTo(userId);
  }

  @Test
  void adminViewerLogin_jwtHasViewerTierClaim() throws Exception {
    String identifier = "auth-it-viewer@example.com";
    String password = "viewer-password";
    seedAdmin(identifier, password, AdminTier.VIEWER);

    MvcResult result =
        mockMvc
            .perform(
                post(IdentityRoutes.AUTH_LOGIN)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(
                        objectMapper.writeValueAsString(new LoginRequest(identifier, password))))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode payload =
        decodeJwtPayload(
            objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("authToken")
                .asText());
    assertThat(payload.get("tier").asText()).isEqualTo("VIEWER");
  }

  private UUID seedAdmin(String identifier, String password, AdminTier tier) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setIdentifier(identifier);
    user.setIdentifierType(IdentifierType.EMAIL);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setUserType(UserType.ADMIN);
    user.setStatus(UserStatus.ACTIVE);
    user.setMfaEnabled(false);
    userRepository.save(user);

    AdminProfile profile = new AdminProfile();
    profile.setId(UUID.randomUUID());
    profile.setUserId(user.getId());
    profile.setDepartment("AuthIT");
    profile.setEmployeeId("EMP-" + UUID.randomUUID());
    profile.setAdminTier(tier);
    adminProfileRepository.save(profile);

    return user.getId();
  }

  private JsonNode decodeJwtPayload(String jwt) throws Exception {
    String[] parts = jwt.split("\\.");
    assertThat(parts).hasSize(3);
    byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
    return objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
  }
}
