package pay.conflux.backend.risk;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pay.conflux.backend.risk.dto.AddBlacklistEntryRequest;
import pay.conflux.backend.risk.dto.CreateRiskRuleRequest;
import pay.conflux.backend.risk.dto.UpsertMerchantRiskProfileRequest;
import pay.conflux.backend.risk.engine.VelocityCounter;
import pay.conflux.backend.risk.engine.VelocityDimension;
import pay.conflux.backend.risk.entity.BlacklistEntry;
import pay.conflux.backend.risk.entity.BlacklistType;
import pay.conflux.backend.risk.entity.RuleAction;
import pay.conflux.backend.risk.entity.TrustLevel;
import pay.conflux.backend.risk.repository.BlacklistEntryRepository;
import pay.conflux.backend.risk.repository.MerchantRiskProfileRepository;
import pay.conflux.backend.risk.repository.RiskRuleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AdminRiskIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Autowired private RiskRuleRepository riskRuleRepository;
  @Autowired private BlacklistEntryRepository blacklistEntryRepository;
  @Autowired private MerchantRiskProfileRepository merchantRiskProfileRepository;
  @Autowired private VelocityCounter velocityCounter;

  @BeforeEach
  void setUp() {
    riskRuleRepository.deleteAll();
    blacklistEntryRepository.deleteAll();
    merchantRiskProfileRepository.deleteAll();
  }

  @Test
  void testVelocityCounterRace() throws InterruptedException {
    int threadCount = 1000;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    ExecutorService executorService = Executors.newFixedThreadPool(32);
    UUID merchantId = UUID.randomUUID();

    long[] maxResult = new long[1];

    for (int i = 0; i < threadCount; i++) {
      executorService.submit(
          () -> {
            try {
              startLatch.await();
              long result =
                  velocityCounter.incrementAndGet(merchantId, VelocityDimension.PER_MERCHANT, 60);
              synchronized (maxResult) {
                if (result > maxResult[0]) {
                  maxResult[0] = result;
                }
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    doneLatch.await();
    executorService.shutdown();

    assertEquals(1000L, maxResult[0]);
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void testRiskRuleLifecycle() throws Exception {
    // Create 5 rules
    for (int i = 1; i <= 5; i++) {
      CreateRiskRuleRequest req =
          new CreateRiskRuleRequest("Rule " + i, "true", i * 10, RuleAction.BLOCK);
      mockMvc
          .perform(
              post("/api/v1/admin/risk/rules")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(req)))
          .andExpect(status().isCreated());
    }

    // List them
    String jsonResponse =
        mockMvc
            .perform(get("/api/v1/admin/risk/rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(5))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String firstRuleId = objectMapper.readTree(jsonResponse).get("data").get(0).get("id").asText();

    // Disable one
    mockMvc.perform(delete("/api/v1/admin/risk/rules/" + firstRuleId)).andExpect(status().isOk());

    // List them (4 active)
    mockMvc
        .perform(get("/api/v1/admin/risk/rules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(4));
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void testBlacklistLifecycle() throws Exception {
    AddBlacklistEntryRequest req =
        new AddBlacklistEntryRequest(
            BlacklistType.PHONE, "1234567890", "Fraud", Instant.now().plus(1, ChronoUnit.DAYS));

    mockMvc
        .perform(
            post("/api/v1/admin/risk/blacklist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated());

    // List returns it
    String jsonResponse =
        mockMvc
            .perform(get("/api/v1/admin/risk/blacklist"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String entryId = objectMapper.readTree(jsonResponse).get("data").get(0).get("id").asText();

    // Expire it directly via repo since there's no endpoint for expiry editing yet
    BlacklistEntry entry =
        blacklistEntryRepository.findById(UUID.fromString(entryId)).orElseThrow();
    entry.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
    blacklistEntryRepository.save(entry);

    // List excludes it
    mockMvc
        .perform(get("/api/v1/admin/risk/blacklist"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  @WithMockUser(authorities = "ADMIN_MANAGER")
  void testMerchantProfileUpsert() throws Exception {
    UUID merchantId = UUID.randomUUID();
    UpsertMerchantRiskProfileRequest req =
        new UpsertMerchantRiskProfileRequest(TrustLevel.TRUSTED, "limit1");

    // Upsert first time
    mockMvc
        .perform(
            put("/api/v1/admin/risk/profiles/" + merchantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk());

    assertEquals(1, merchantRiskProfileRepository.count());

    // Upsert second time
    UpsertMerchantRiskProfileRequest req2 =
        new UpsertMerchantRiskProfileRequest(TrustLevel.VIP, "limit2");
    mockMvc
        .perform(
            put("/api/v1/admin/risk/profiles/" + merchantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
        .andExpect(status().isOk());

    assertEquals(1, merchantRiskProfileRepository.count());
    assertEquals(TrustLevel.VIP, merchantRiskProfileRepository.findAll().get(0).getTrustLevel());
  }
}
