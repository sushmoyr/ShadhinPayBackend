package pay.conflux.backend.adapters.bkash;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;
import pay.conflux.backend.adapters.port.VendorPaymentRequest;
import pay.conflux.backend.adapters.port.VendorResponse;
import pay.conflux.backend.adapters.port.VendorStatus;
import pay.conflux.backend.adapters.support.AdapterResilience;
import pay.conflux.backend.adapters.support.HttpClientFactory;
import pay.conflux.backend.adapters.support.RedisTokenService;
import pay.conflux.backend.common.money.Money;

/**
 * Verifies {@link BkashAdapter} cooperates with the Redis-backed {@link RedisTokenService} so that
 * two consecutive {@code initiate} calls only invoke {@code /grant} once.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
class BkashAdapterIntegrationTest {

  @RegisterExtension
  static final WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redisTemplate;

  private BkashAdapter adapter;

  @BeforeAll
  static void startRedis() {
    RedisStandaloneConfiguration config =
        new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379));
    connectionFactory = new LettuceConnectionFactory(config);
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
  }

  @AfterAll
  static void stopRedis() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @BeforeEach
  void setUp() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    HttpClientFactory factory = new HttpClientFactory();
    CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
    registry.circuitBreaker(Vendor.BKASH.name()).transitionToClosedState();
    AdapterResilience resilience = new AdapterResilience(registry);
    ObjectMapper objectMapper = new ObjectMapper();
    BkashAuthClient authClient = new BkashAuthClient(factory, objectMapper);
    RedisTokenService tokenService = new RedisTokenService(redisTemplate, authClient);
    adapter = new BkashAdapter(factory, tokenService, authClient, resilience, objectMapper);

    wm.stubFor(
        post(urlEqualTo("/tokenized/checkout/token/grant"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id_token\":\"cached-tok\",\"expires_in\":3600,"
                            + "\"refresh_token\":\"r\",\"token_type\":\"Bearer\"}")));
    wm.stubFor(
        post(urlEqualTo("/tokenized/checkout/create"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"statusCode\":\"0000\",\"paymentID\":\"PMT_OK\","
                            + "\"bkashURL\":\"https://x\"}")));
  }

  @Test
  void cachesTokenAcrossCalls() {
    VendorCredentials creds =
        new VendorCredentials(
            Map.of(
                "appKey",
                "AK",
                "appSecret",
                "AS",
                "username",
                "u",
                "password",
                "p",
                "baseUrl",
                wm.baseUrl()));

    VendorResponse r1 = adapter.initiate(payment(), creds);
    VendorResponse r2 = adapter.initiate(payment(), creds);

    assertThat(r1.status()).isEqualTo(VendorStatus.INITIATED);
    assertThat(r2.status()).isEqualTo(VendorStatus.INITIATED);
    wm.verify(1, postRequestedFor(urlEqualTo("/tokenized/checkout/token/grant")));
    wm.verify(2, postRequestedFor(urlEqualTo("/tokenized/checkout/create")));
  }

  private VendorPaymentRequest payment() {
    return new VendorPaymentRequest(
        UUID.randomUUID(),
        Money.of(new BigDecimal("100.00"), "BDT"),
        "ORDER-IT",
        "https://merchant.example/callback",
        Map.of());
  }
}
