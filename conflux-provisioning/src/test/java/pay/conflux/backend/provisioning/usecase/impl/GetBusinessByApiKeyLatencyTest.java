package pay.conflux.backend.provisioning.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import pay.conflux.backend.provisioning.repository.ApiKeyRepository;
import pay.conflux.backend.provisioning.repository.BusinessRepository;
import pay.conflux.backend.provisioning.usecase.BusinessContext;

/**
 * Warm-cache latency microbenchmark for {@link GetBusinessByApiKeyUseCaseImpl}. We measure the
 * cache-hit path because that's what defines the steady-state hot-path budget — the p95 must stay
 * below the 50 ms gate from PRD §5. The DB-miss path is unbounded by design (it depends on
 * Postgres) and exercised in the unit and integration tests.
 *
 * <p>Histogram printed to stdout shows p50/p90/p95/p99/max in microseconds. Failing this test means
 * a non-trivial regression in serialization or Redis access.
 */
@ExtendWith(MockitoExtension.class)
class GetBusinessByApiKeyLatencyTest {

  @Mock private ApiKeyRepository apiKeyRepository;
  @Mock private BusinessRepository businessRepository;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOps;
  @Mock private ApiKeyUsageTracker usageTracker;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GetBusinessByApiKeyUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new GetBusinessByApiKeyUseCaseImpl(
            apiKeyRepository, businessRepository, redisTemplate, usageTracker, objectMapper);
  }

  @Test
  void warmCache_p95_under50ms() throws Exception {
    String plaintext = "sp_test_warmcache__________________________";
    BusinessContext cached =
        new BusinessContext(UUID.randomUUID(), UUID.randomUUID(), "TEST", "https://h.example/cb");
    String cachedJson = objectMapper.writeValueAsString(cached);

    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(anyString())).thenReturn(cachedJson);

    int warmup = 200;
    for (int i = 0; i < warmup; i++) {
      useCase.execute(plaintext);
    }

    int iterations = 1_000;
    long[] samplesNs = new long[iterations];
    for (int i = 0; i < iterations; i++) {
      long t0 = System.nanoTime();
      BusinessContext result = useCase.execute(plaintext);
      samplesNs[i] = System.nanoTime() - t0;
      assertThat(result.environment()).isEqualTo("TEST");
    }
    Arrays.sort(samplesNs);

    long p50us = samplesNs[(int) (iterations * 0.50)] / 1_000;
    long p90us = samplesNs[(int) (iterations * 0.90)] / 1_000;
    long p95us = samplesNs[(int) (iterations * 0.95)] / 1_000;
    long p99us = samplesNs[(int) (iterations * 0.99)] / 1_000;
    long maxUs = samplesNs[iterations - 1] / 1_000;

    System.out.printf(
        "[GetBusinessByApiKey warm-cache] iter=%d  p50=%dus  p90=%dus  p95=%dus  p99=%dus "
            + " max=%dus%n",
        iterations, p50us, p90us, p95us, p99us, maxUs);

    long p95Ms = p95us / 1_000;
    assertThat(p95Ms)
        .as("warm-cache p95 must be well under the 50 ms hot-path budget")
        .isLessThan(50L);
  }
}
