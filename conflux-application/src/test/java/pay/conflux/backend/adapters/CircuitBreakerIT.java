package pay.conflux.backend.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pay.conflux.backend.adapters.config.ResilienceConfig;
import pay.conflux.backend.adapters.error.MfsAdapterException;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.support.AdapterResilience;
import pay.conflux.backend.common.error.ErrorCode;

@SpringBootTest(classes = {ResilienceConfig.class, AdapterResilience.class})
class CircuitBreakerIT {

  @Autowired private AdapterResilience resilience;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("resilience.wait-duration", () -> "1s");
  }

  @Test
  void testCircuitBreakerFlow() throws InterruptedException {
    // Force 11 consecutive failures
    for (int i = 0; i < 11; i++) {
      assertThrows(
          MfsAdapterException.class,
          () ->
              resilience.executeWithCircuitBreaker(
                  Vendor.MOCK,
                  () -> {
                    throw new RuntimeException("boom");
                  }));
    }

    // 12th call short-circuits with VENDOR_DOWN
    AtomicBoolean twelfthInvoked = new AtomicBoolean(false);
    MfsAdapterException thrown =
        assertThrows(
            MfsAdapterException.class,
            () ->
                resilience.executeWithCircuitBreaker(
                    Vendor.MOCK,
                    () -> {
                      twelfthInvoked.set(true);
                      throw new RuntimeException("should not execute");
                    }));

    assertEquals(ErrorCode.VENDOR_DOWN, thrown.getErrorCode());
    assertEquals("Circuit open for MOCK", thrown.getMessage());
    assertFalse(twelfthInvoked.get(), "callable must not be invoked when circuit is open");

    // Wait waitDurationInOpenState (1 second)
    Thread.sleep(1100);

    // Transitions to half-open and accepts a probe
    String result = resilience.executeWithCircuitBreaker(Vendor.MOCK, () -> "success");
    assertEquals("success", result);
  }
}
