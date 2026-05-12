package pay.conflux.backend.adapters.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ResilienceConfig.class)
class ResilienceConfigTest {

  @Autowired private CircuitBreakerRegistry registry;

  @Test
  void registrySeedsSslcommerzCircuitBreaker() {
    assertThat(registry.find("SSLCOMMERZ")).isPresent();
  }
}
