package com.shadhinpay.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shadhinpay.identity.constant.IdentityRoutes;
import com.shadhinpay.identity.dto.BlockUserRequest;
import com.shadhinpay.identity.dto.KycSubmissionRequest;
import com.shadhinpay.identity.dto.LoginRequest;
import com.shadhinpay.identity.dto.RegisterMerchantRequest;
import com.shadhinpay.identity.events.MerchantVerifiedEvent;
import com.shadhinpay.identity.events.UserBlockedEvent;
import com.shadhinpay.identity.repository.MerchantProfileRepository;
import com.shadhinpay.identity.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.PublishedEvents;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ApplicationModuleTest // Ensures events are captured
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Disabled("Testcontainers not available locally")
class IdentityIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add(
        "shadhinpay.auth.token-secret", () -> "test-secret-key-at-least-32-chars-long-!!! ");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private MerchantProfileRepository merchantProfileRepository;

  @Test
  void registerAndLoginPolymorphicAuth() throws Exception {
    // 1. Register with Phone
    RegisterMerchantRequest phoneRequest =
        new RegisterMerchantRequest("01712345678", "password123", "Phone Merchant");
    mockMvc
        .perform(
            post(IdentityRoutes.MERCHANT_REGISTER)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(phoneRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.identifier").value("01712345678"))
        .andExpect(jsonPath("$.data.identifierType").value("PHONE"));

    // 2. Register with Email
    RegisterMerchantRequest emailRequest =
        new RegisterMerchantRequest("merchant@example.com", "password123", "Email Merchant");
    mockMvc
        .perform(
            post(IdentityRoutes.MERCHANT_REGISTER)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(emailRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.identifier").value("merchant@example.com"))
        .andExpect(jsonPath("$.data.identifierType").value("EMAIL"));

    // 3. Login with Phone
    LoginRequest phoneLogin = new LoginRequest("01712345678", "password123");
    mockMvc
        .perform(
            post(IdentityRoutes.AUTH_LOGIN)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(phoneLogin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.authToken").exists());

    // 4. Login with Email
    LoginRequest emailLogin = new LoginRequest("merchant@example.com", "password123");
    mockMvc
        .perform(
            post(IdentityRoutes.AUTH_LOGIN)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(emailLogin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.authToken").exists());
  }

  @Test
  void softDeleteAllowsReuseOfIdentifier() throws Exception {
    String identifier = "01912345678";
    RegisterMerchantRequest request =
        new RegisterMerchantRequest(identifier, "password123", "Soft Delete Test");

    // 1. First registration
    mockMvc
        .perform(
            post(IdentityRoutes.MERCHANT_REGISTER)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    // 2. Soft delete the user
    var user =
        userRepository
            .findByIdentifierAndIdentifierTypeAndDeletedFalse(
                identifier, com.shadhinpay.identity.entity.enums.IdentifierType.PHONE)
            .orElseThrow();
    user.setDeleted(true);
    userRepository.save(user);

    // 3. Register again with same identifier - should succeed
    mockMvc
        .perform(
            post(IdentityRoutes.MERCHANT_REGISTER)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void kycLifecycleAndAdminVerification(PublishedEvents events) throws Exception {
    String identifier = "kyc-test@example.com";
    RegisterMerchantRequest request =
        new RegisterMerchantRequest(identifier, "password123", "KYC Test Merchant");

    // 1. Register Merchant
    String res =
        mockMvc
            .perform(
                post(IdentityRoutes.MERCHANT_REGISTER)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID userId = UUID.fromString(objectMapper.readTree(res).path("data").path("userId").asText());

    var profile = merchantProfileRepository.findByUserId(userId).orElseThrow();
    UUID profileId = profile.getId();

    // 2. Submit KYC
    KycSubmissionRequest kycRequest =
        new KycSubmissionRequest(
            "https://example.com/nid-front.jpg",
            "https://example.com/nid-back.jpg",
            "https://example.com/trade-license.jpg",
            null);

    mockMvc
        .perform(
            post(IdentityRoutes.MERCHANT_KYC)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(kycRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.onboardingStatus").value("PENDING_VERIFICATION"));

    // 3. Admin Verify
    mockMvc
        .perform(
            post(IdentityRoutes.ADMIN_MERCHANTS_VERIFY.replace("{id}", profileId.toString()))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isOk());

    // 4. Assert Event Publication
    var verifiedEvents = events.ofType(MerchantVerifiedEvent.class);
    assertThat(verifiedEvents).hasSize(1);
    assertThat(verifiedEvents.iterator().next().merchantProfileId()).isEqualTo(profileId);
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void blockUserIsIdempotentAndPublishesEventOnce(PublishedEvents events) throws Exception {
    String identifier = "block-test@example.com";
    RegisterMerchantRequest request =
        new RegisterMerchantRequest(identifier, "password123", "Block Test Merchant");

    String res =
        mockMvc
            .perform(
                post(IdentityRoutes.MERCHANT_REGISTER)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID userId = UUID.fromString(objectMapper.readTree(res).path("data").path("userId").asText());

    // 1. Block user first time
    BlockUserRequest blockRequest = new BlockUserRequest("Suspicious activity");
    mockMvc
        .perform(
            post(IdentityRoutes.ADMIN_USERS_BLOCK.replace("{id}", userId.toString()))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(blockRequest)))
        .andExpect(status().isOk());

    var blockedEvents = events.ofType(UserBlockedEvent.class);
    assertThat(blockedEvents).hasSize(1);
    assertThat(blockedEvents.iterator().next().reason()).isEqualTo("Suspicious activity");

    // 2. Block user second time (idempotent)
    mockMvc
        .perform(
            post(IdentityRoutes.ADMIN_USERS_BLOCK.replace("{id}", userId.toString()))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(blockRequest)))
        .andExpect(status().isOk());

    // Event size should still be 1
    blockedEvents = events.ofType(UserBlockedEvent.class);
    assertThat(blockedEvents).hasSize(1);
  }
}
