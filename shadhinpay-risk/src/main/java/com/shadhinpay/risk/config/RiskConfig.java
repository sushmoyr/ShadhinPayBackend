package com.shadhinpay.risk.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class RiskConfig {

  @Value("${shadhinpay.risk.flag-threshold:50}")
  private int flagThreshold;

  @Bean
  public int flagThreshold() {
    return flagThreshold;
  }

  @Bean(destroyMethod = "shutdown")
  public ExecutorService spelExecutorService() {
    ThreadFactory threadFactory =
        r -> {
          Thread t = new Thread(r, "spel-executor");
          t.setDaemon(true);
          return t;
        };
    return Executors.newFixedThreadPool(8, threadFactory);
  }
}
