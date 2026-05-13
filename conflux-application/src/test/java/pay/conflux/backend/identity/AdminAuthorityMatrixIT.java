package pay.conflux.backend.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pay.conflux.backend.identity.constant.IdentityRoutes;
import pay.conflux.backend.identity.dto.CreateAdminRequest;
import pay.conflux.backend.identity.dto.LoginRequest;
import pay.conflux.backend.identity.dto.RegisterMerchantRequest;
import pay.conflux.backend.identity.dto.UpdateAdminTierRequest;
import pay.conflux.backend.identity.entity.AdminProfile;
import pay.conflux.backend.identity.entity.MerchantProfile;
import pay.conflux.backend.identity.entity.User;
import pay.conflux.backend.identity.enums.AdminTier;
import pay.conflux.backend.identity.enums.IdentifierType;
import pay.conflux.backend.identity.enums.OnboardingStatus;
import pay.conflux.backend.identity.enums.UserStatus;
import pay.conflux.backend.identity.enums.UserType;
import pay.conflux.backend.identity.repository.AdminProfileRepository;
import pay.conflux.backend.identity.repository.MerchantProfileRepository;
import pay.conflux.backend.identity.repository.UserRepository;

/**
 * Walks the full authority matrix from {@code DOCS/features/identity/TECH_SPEC.md §4.3}:
 *
 * <pre>
 * Endpoint                            MERCHANT  VIEWER  MANAGER  SUPER
 * GET /admin/merchants                  403      200     200     200
 * POST /admin/merchants/{id}/verify     403      403     200     200
 * GET /admin/admins                     403      200     200     200
 * POST /admin/admins                    403      403     403     200
 * PATCH /admin/admins/{id}/tier         403      403     403     200
 * POST /admin/admins/{id}/disable       403      403     403     200
 * GET /admin/me                         403      200     200     200
 * </pre>
 *
 * <p>JWTs are minted by hitting {@code POST /api/v1/auth/login} so the test exercises the real
 * issuance + verification path end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
@ActiveProfiles("test")
class AdminAuthorityMatrixIT {

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
  @Autowired private MerchantProfileRepository merchantProfileRepository;
  @Autowired private AdminProfileRepository adminProfileRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private String merchantJwt;
  private String viewerJwt;
  private String managerJwt;
  private String superJwt;
  private UUID merchantProfileId;
  private UUID viewerUserId;

  @BeforeEach
  void seed() throws Exception {
    // Fresh merchant via the public registration endpoint, then login to get a JWT.
    String merchantEmail = "matrix-merchant-" + UUID.randomUUID() + "@example.com";
    mockMvc
        .perform(
            post(IdentityRoutes.MERCHANT_REGISTER)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterMerchantRequest(
                            merchantEmail, "matrix-password", "Matrix Merchant"))))
        .andExpect(status().isCreated());
    merchantJwt = login(merchantEmail, "matrix-password");

    UUID merchantUserId =
        userRepository
            .findByIdentifierAndIdentifierTypeAndDeletedFalse(merchantEmail, IdentifierType.EMAIL)
            .orElseThrow()
            .getId();
    MerchantProfile profile = merchantProfileRepository.findByUserId(merchantUserId).orElseThrow();
    // Move the merchant to PENDING_VERIFICATION so the MANAGER/SUPER verify call has a valid
    // target (the verify use case rejects rows still in DRAFT).
    profile.setOnboardingStatus(OnboardingStatus.PENDING_VERIFICATION);
    merchantProfileRepository.save(profile);
    merchantProfileId = profile.getId();

    viewerUserId = seedAdmin("matrix-viewer-", AdminTier.VIEWER).getId();
    User managerUser = seedAdmin("matrix-manager-", AdminTier.MANAGER);
    // Seed two SUPER admins so disable/tier mutations on the SUPER actor don't trip the last-SUPER
    // guard; we never actually disable the actor.
    User superUserA = seedAdmin("matrix-super-a-", AdminTier.SUPER);
    seedAdmin("matrix-super-b-", AdminTier.SUPER);

    viewerJwt = login(viewerUserId, "tier-password");
    managerJwt = login(managerUser.getId(), "tier-password");
    superJwt = login(superUserA.getId(), "tier-password");
  }

  @Test
  void getAdminMerchants_authorityMatrix() throws Exception {
    expectStatus(get(IdentityRoutes.ADMIN_MERCHANTS), merchantJwt, 403);
    expectStatus(get(IdentityRoutes.ADMIN_MERCHANTS), viewerJwt, 200);
    expectStatus(get(IdentityRoutes.ADMIN_MERCHANTS), managerJwt, 200);
    expectStatus(get(IdentityRoutes.ADMIN_MERCHANTS), superJwt, 200);
  }

  @Test
  void verifyMerchant_authorityMatrix() throws Exception {
    String path =
        IdentityRoutes.ADMIN_MERCHANTS_VERIFY.replace("{id}", merchantProfileId.toString());
    expectStatus(post(path), merchantJwt, 403);
    expectStatus(post(path), viewerJwt, 403);
    expectStatus(post(path), managerJwt, 200); // verify happens here
    // Re-call from SUPER on the same row is idempotent; either 200 or another success status is
    // fine
    // — what matters for authority assertion is *not* 403.
    MvcResult superResult =
        mockMvc.perform(post(path).header("Authorization", "Bearer " + superJwt)).andReturn();
    int superStatus = superResult.getResponse().getStatus();
    org.assertj.core.api.Assertions.assertThat(superStatus).isNotEqualTo(403);
  }

  @Test
  void listAdmins_authorityMatrix() throws Exception {
    expectStatus(get(IdentityRoutes.ADMIN_ADMINS), merchantJwt, 403);
    expectStatus(get(IdentityRoutes.ADMIN_ADMINS), viewerJwt, 200);
    expectStatus(get(IdentityRoutes.ADMIN_ADMINS), managerJwt, 200);
    expectStatus(get(IdentityRoutes.ADMIN_ADMINS), superJwt, 200);
  }

  @Test
  void createAdmin_authorityMatrix() throws Exception {
    CreateAdminRequest body =
        new CreateAdminRequest(
            "created-by-matrix-" + UUID.randomUUID() + "@example.com",
            "fresh-password",
            "Risk",
            "EMP-" + UUID.randomUUID(),
            AdminTier.VIEWER);
    String json = objectMapper.writeValueAsString(body);
    RequestSupplier req =
        () ->
            post(IdentityRoutes.ADMIN_ADMINS).contentType(MediaType.APPLICATION_JSON).content(json);
    expectStatusBuilt(req, merchantJwt, 403);
    expectStatusBuilt(req, viewerJwt, 403);
    expectStatusBuilt(req, managerJwt, 403);

    // SUPER: only this call actually creates an admin — reuse a brand-new identifier so the
    // duplicate-resource guard doesn't fire if the test re-runs in the same suite.
    CreateAdminRequest superBody =
        new CreateAdminRequest(
            "super-created-" + UUID.randomUUID() + "@example.com",
            "fresh-password",
            "Risk",
            "EMP-" + UUID.randomUUID(),
            AdminTier.VIEWER);
    mockMvc
        .perform(
            post(IdentityRoutes.ADMIN_ADMINS)
                .header("Authorization", "Bearer " + superJwt)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(superBody)))
        .andExpect(MockMvcResultMatchers.status().isCreated());
  }

  @Test
  void updateAdminTier_authorityMatrix() throws Exception {
    AdminProfile viewerProfile = adminProfileRepository.findByUserId(viewerUserId).orElseThrow();
    String path =
        IdentityRoutes.ADMIN_ADMINS_TIER.replace("{id}", viewerProfile.getId().toString());
    String body =
        objectMapper.writeValueAsString(new UpdateAdminTierRequest(AdminTier.VIEWER)); // same-tier
    RequestSupplier req = () -> patch(path).contentType(MediaType.APPLICATION_JSON).content(body);

    expectStatusBuilt(req, merchantJwt, 403);
    expectStatusBuilt(req, viewerJwt, 403);
    expectStatusBuilt(req, managerJwt, 403);
    expectStatusBuilt(req, superJwt, 200); // idempotent same-tier
  }

  @Test
  void disableAdmin_authorityMatrix() throws Exception {
    AdminProfile viewerProfile = adminProfileRepository.findByUserId(viewerUserId).orElseThrow();
    String path =
        IdentityRoutes.ADMIN_ADMINS_DISABLE.replace("{id}", viewerProfile.getId().toString());
    RequestSupplier req = () -> post(path);

    expectStatusBuilt(req, merchantJwt, 403);
    expectStatusBuilt(req, viewerJwt, 403);
    expectStatusBuilt(req, managerJwt, 403);
    expectStatusBuilt(req, superJwt, 200); // targets a non-SUPER, so last-SUPER guard is irrelevant
  }

  @Test
  void adminMe_authorityMatrix() throws Exception {
    expectStatus(get(IdentityRoutes.ADMIN_ME), merchantJwt, 403);
    expectStatus(get(IdentityRoutes.ADMIN_ME), viewerJwt, 200);
    expectStatus(get(IdentityRoutes.ADMIN_ME), managerJwt, 200);
    expectStatus(get(IdentityRoutes.ADMIN_ME), superJwt, 200);
  }

  // ---------- helpers ----------

  private void expectStatus(MockHttpServletRequestBuilder request, String jwt, int expectedStatus)
      throws Exception {
    mockMvc
        .perform(request.header("Authorization", "Bearer " + jwt))
        .andExpect(MockMvcResultMatchers.status().is(expectedStatus));
  }

  /** Functional builder so the same request can be re-issued per JWT. */
  @FunctionalInterface
  private interface RequestSupplier {
    MockHttpServletRequestBuilder get();
  }

  private void expectStatusBuilt(RequestSupplier supplier, String jwt, int expectedStatus)
      throws Exception {
    mockMvc
        .perform(supplier.get().header("Authorization", "Bearer " + jwt))
        .andExpect(MockMvcResultMatchers.status().is(expectedStatus));
  }

  private String login(String identifier, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(IdentityRoutes.AUTH_LOGIN)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(
                        objectMapper.writeValueAsString(new LoginRequest(identifier, password))))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("authToken")
        .asText();
  }

  private String login(UUID userId, String password) throws Exception {
    User user = userRepository.findById(userId).orElseThrow();
    return login(user.getIdentifier(), password);
  }

  private User seedAdmin(String prefix, AdminTier tier) {
    User u = new User();
    u.setId(UUID.randomUUID());
    u.setIdentifier(prefix + UUID.randomUUID() + "@example.com");
    u.setIdentifierType(IdentifierType.EMAIL);
    u.setPasswordHash(passwordEncoder.encode("tier-password"));
    u.setUserType(UserType.ADMIN);
    u.setStatus(UserStatus.ACTIVE);
    u.setMfaEnabled(false);
    userRepository.save(u);

    AdminProfile profile = new AdminProfile();
    profile.setId(UUID.randomUUID());
    profile.setUserId(u.getId());
    profile.setDepartment("Matrix");
    profile.setEmployeeId("EMP-" + UUID.randomUUID());
    profile.setAdminTier(tier);
    adminProfileRepository.save(profile);
    return u;
  }
}
