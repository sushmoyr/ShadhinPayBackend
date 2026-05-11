package com.shadhinpay.adapters;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.shadhinpay.adapters.port.Vendor;
import com.shadhinpay.adapters.port.VendorCredentials;
import com.shadhinpay.adapters.support.HttpClientFactory;
import com.shadhinpay.adapters.support.RedisTokenService;
import com.shadhinpay.adapters.support.VendorAuthClient;
import com.shadhinpay.adapters.wiremock.VendorScenarios;
import com.shadhinpay.adapters.wiremock.VendorWireMockExtension;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    classes = {RedisTokenService.class, HttpClientFactory.class, MockTokenRefreshIT.Config.class})
@ImportAutoConfiguration(RedisAutoConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisabledIfSystemProperty(named = "skipDocker", matches = "true")
class MockTokenRefreshIT {

  @RegisterExtension static VendorWireMockExtension wireMock = new VendorWireMockExtension();

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProps(DynamicPropertyRegistry r) {
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired RedisTokenService tokenService;
  @Autowired StringRedisTemplate redisTemplate;

  @BeforeEach
  void flushRedis() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
  }

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    VendorAuthClient wireMockAuthClient(HttpClientFactory factory) {
      return new WireMockMockVendorAuthClient(factory, wireMock.getBaseUrl());
    }
  }

  /**
   * Test-only override of {@code MockVendorAuthClient} that hits the WireMock URL and retries once
   * on a 401 response. Used to exercise the {@code unauthorizedThenSuccess} scenario end-to-end.
   */
  static class WireMockMockVendorAuthClient implements VendorAuthClient {

    private final HttpClientFactory factory;
    private final String baseUrl;

    WireMockMockVendorAuthClient(HttpClientFactory factory, String baseUrl) {
      this.factory = factory;
      this.baseUrl = baseUrl;
    }

    @Override
    public AuthToken authenticate(Vendor v, VendorCredentials creds) {
      OkHttpClient client = factory.clientFor(v);
      Request req =
          new Request.Builder()
              .url(baseUrl + "/mock/auth")
              .post(RequestBody.create(new byte[0], null))
              .build();

      try (Response first = client.newCall(req).execute()) {
        if (first.code() == 200) {
          return new AuthToken("wiremock-token", Instant.now().plusSeconds(3600));
        }
        if (first.code() != 401) {
          throw new IllegalStateException("unexpected auth response: " + first.code());
        }
      } catch (IOException e) {
        throw new IllegalStateException("auth call failed", e);
      }

      try (Response retry = client.newCall(req).execute()) {
        if (retry.code() == 200) {
          return new AuthToken("wiremock-token", Instant.now().plusSeconds(3600));
        }
        throw new IllegalStateException("auth retry failed with " + retry.code());
      } catch (IOException e) {
        throw new IllegalStateException("auth retry failed", e);
      }
    }
  }

  @Test
  void testUnauthorizedThenSuccess() {
    VendorScenarios.unauthorizedThenSuccess(Vendor.MOCK);

    VendorCredentials creds = new VendorCredentials(Map.of("key", "val"));
    String token = tokenService.getToken(Vendor.MOCK, creds);

    assertEquals("wiremock-token", token);

    com.github.tomakehurst.wiremock.client.WireMock.verify(
        2, postRequestedFor(urlPathMatching("/mock/auth")));
  }
}
