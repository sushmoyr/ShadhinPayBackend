package com.shadhinpay.adapters.support;

import com.shadhinpay.adapters.error.MfsAdapterException;
import com.shadhinpay.adapters.port.Vendor;
import com.shadhinpay.common.error.ErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;

@Component
public class AdapterResilience {

  private final CircuitBreakerRegistry registry;

  public AdapterResilience(CircuitBreakerRegistry registry) {
    this.registry = registry;
  }

  public <T> T executeWithCircuitBreaker(Vendor v, Callable<T> call) throws MfsAdapterException {
    CircuitBreaker circuitBreaker = registry.circuitBreaker(v.name());
    try {
      return circuitBreaker.executeCallable(call);
    } catch (MfsAdapterException e) {
      throw e;
    } catch (CallNotPermittedException e) {
      throw new MfsAdapterException(ErrorCode.VENDOR_DOWN, "Circuit open for " + v, e);
    } catch (Exception e) {
      throw new MfsAdapterException(ErrorCode.MFS_ADAPTER_FAILURE, e.getMessage(), e);
    }
  }
}
