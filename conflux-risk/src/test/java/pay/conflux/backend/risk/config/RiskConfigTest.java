package pay.conflux.backend.risk.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;

class RiskConfigTest {

  @Test
  void shouldProvideBeans() {
    RiskConfig config = new RiskConfig();

    assertThat(config.flagThreshold()).isEqualTo(0); // Default when not injected via @Value

    ExecutorService executor = config.spelExecutorService();
    assertThat(executor).isNotNull();
    executor.shutdown();
  }
}
