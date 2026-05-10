package com.shadhinpay.adapters.support;

import static org.junit.jupiter.api.Assertions.*;

import com.shadhinpay.adapters.error.MfsAdapterException;
import com.shadhinpay.adapters.port.Vendor;
import com.shadhinpay.common.error.ErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdapterResilienceTest {

  private CircuitBreakerRegistry registry;
  private AdapterResilience resilience;

  @BeforeEach
  void setUp() {
    registry = CircuitBreakerRegistry.ofDefaults();
    resilience = new AdapterResilience(registry);
  }

  @Test
  void testSuccess() throws MfsAdapterException {
    String result = resilience.executeWithCircuitBreaker(Vendor.MOCK, () -> "success");
    assertEquals("success", result);
  }

  @Test
  void testMfsAdapterExceptionPropagation() {
    MfsAdapterException ex = new MfsAdapterException(ErrorCode.UNAUTHORIZED, "Unauthorized");
    MfsAdapterException thrown =
        assertThrows(
            MfsAdapterException.class,
            () ->
                resilience.executeWithCircuitBreaker(
                    Vendor.MOCK,
                    () -> {
                      throw ex;
                    }));
    assertEquals(ErrorCode.UNAUTHORIZED, thrown.getErrorCode());
    assertEquals("Unauthorized", thrown.getMessage());
  }

  @Test
  void testOtherExceptionWrapping() {
    MfsAdapterException thrown =
        assertThrows(
            MfsAdapterException.class,
            () ->
                resilience.executeWithCircuitBreaker(
                    Vendor.MOCK,
                    () -> {
                      throw new RuntimeException("boom");
                    }));
    assertEquals(ErrorCode.MFS_ADAPTER_FAILURE, thrown.getErrorCode());
    assertEquals("boom", thrown.getMessage());
    assertTrue(thrown.getCause() instanceof RuntimeException);
  }

  @Test
  void testCircuitOpen() {
    CircuitBreaker cb = registry.circuitBreaker(Vendor.MOCK.name());
    cb.transitionToOpenState();

    MfsAdapterException thrown =
        assertThrows(
            MfsAdapterException.class,
            () -> resilience.executeWithCircuitBreaker(Vendor.MOCK, () -> "should not run"));
    assertEquals(ErrorCode.VENDOR_DOWN, thrown.getErrorCode());
    assertTrue(thrown.getMessage().contains("Circuit open"));
  }
}
