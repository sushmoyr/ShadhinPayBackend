package pay.conflux.backend.risk.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class VelocityCounterTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private VelocityCounter counter;
  private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1000000), ZoneId.of("UTC"));

  @BeforeEach
  void setUp() {
    counter = new VelocityCounter(redisTemplate);
    counter.setClock(clock);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  void shouldIncrementAndSetExpireOnFirstCall() {
    UUID merchantId = UUID.randomUUID();
    when(valueOperations.increment(anyString())).thenReturn(1L);

    long val = counter.incrementAndGet(merchantId, VelocityDimension.PER_MERCHANT, 60);

    assertThat(val).isEqualTo(1L);
    verify(redisTemplate).expire(contains("risk:velocity:"), eq(120L), eq(TimeUnit.SECONDS));
  }

  @Test
  void shouldIncrementWithoutExpireOnSubsequentCalls() {
    UUID merchantId = UUID.randomUUID();
    when(valueOperations.increment(anyString())).thenReturn(2L);

    long val = counter.incrementAndGet(merchantId, VelocityDimension.PER_MERCHANT, 60);

    assertThat(val).isEqualTo(2L);
    verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
  }
}
