package com.shadhinpay.adapters;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.shadhinpay.adapters.port.Vendor;
import com.shadhinpay.adapters.port.VendorCredentials;
import com.shadhinpay.adapters.support.HttpClientFactory;
import com.shadhinpay.adapters.support.RedisTokenService;
import com.shadhinpay.adapters.support.VendorAuthClient;
import com.shadhinpay.adapters.wiremock.VendorScenarios;
import com.shadhinpay.adapters.wiremock.VendorWireMockExtension;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SpringBootTest(
    classes = {RedisTokenService.class, HttpClientFactory.class, MockTokenRefreshIT.Config.class})
class MockTokenRefreshIT {

  @RegisterExtension static VendorWireMockExtension wireMock = new VendorWireMockExtension();

  @MockBean StringRedisTemplate redisTemplate;

  @Autowired RedisTokenService tokenService;

  @BeforeEach
  void setUpRedisMock() {
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

    ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    lenient().when(valueOps.get(any())).thenAnswer(inv -> cache.get((String) inv.getArgument(0)));
    lenient()
        .doAnswer(
            inv -> {
              cache.put((String) inv.getArgument(0), (String) inv.getArgument(1));
              return null;
            })
        .when(valueOps)
        .set(any(), any(), any());

    // NX lock logic
    lenient().when(valueOps.setIfAbsent(any(), any(), any())).thenReturn(true);
    // Pretend key never expires for the `getIfValid` check, unless we want to force miss
    // The getExpire returns -2 if key does not exist. We want a cache miss initially.
    lenient().when(redisTemplate.getExpire(any())).thenReturn(-2L);
    lenient().when(redisTemplate.delete(any(String.class))).thenReturn(true);
  }

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    VendorAuthClient wireMockAuthClient(HttpClientFactory factory) {
      return (v, creds) -> {
        OkHttpClient client = factory.clientFor(v);
        Request req =
            new Request.Builder()
                .url(wireMock.getBaseUrl() + "/mock/auth")
                .post(RequestBody.create(new byte[0], null))
                .build();

        try {
          Response res = client.newCall(req).execute();
          if (res.code() == 401) {
            // Retry once
            res.close();
            res = client.newCall(req).execute();
          }
          if (res.code() == 200) {
            res.close();
            return new VendorAuthClient.AuthToken(
                "wiremock-token", Instant.now().plusSeconds(3600));
          }
          res.close();
          throw new RuntimeException("Failed with " + res.code());
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      };
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
