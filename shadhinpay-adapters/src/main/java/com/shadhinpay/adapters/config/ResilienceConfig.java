package com.shadhinpay.adapters.config;

import com.shadhinpay.adapters.port.Vendor;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfig {

  @Value("${resilience.wait-duration:30s}")
  private Duration waitDuration;

  @Bean
  public CircuitBreakerRegistry circuitBreakerRegistry() {
    CircuitBreakerConfig defaultConfig =
        CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(waitDuration)
            .slowCallDurationThreshold(Duration.ofSeconds(10))
            .slowCallRateThreshold(50.0f)
            .permittedNumberOfCallsInHalfOpenState(3)
            .build();

    CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);

    for (Vendor vendor : Vendor.values()) {
      registry.circuitBreaker(vendor.name());
    }

    return registry;
  }
}
