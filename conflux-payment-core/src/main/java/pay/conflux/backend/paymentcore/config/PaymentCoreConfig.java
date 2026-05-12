package pay.conflux.backend.paymentcore.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import pay.conflux.backend.common.observability.TraceIdPropagator;

/**
 * Wiring for the orchestrator's background machinery: the webhook dispatcher's isolated executor,
 * its dedicated {@link OkHttpClient}, and the global hooks for {@code @Scheduled} polling,
 * {@code @Async} dispatch, and {@code @Retryable} optimistic-lock retries on {@code
 * ProcessVendorCallbackUseCase}.
 *
 * <p>The webhook executor is intentionally separate from any cross-module event executor — a slow
 * merchant webhook must not back up the in-memory event bus.
 */
@Configuration
@EnableScheduling
@EnableAsync
@EnableRetry
public class PaymentCoreConfig {

  public static final String WEBHOOK_EXECUTOR = "webhookExecutor";

  @Bean(name = WEBHOOK_EXECUTOR)
  public TaskExecutor webhookExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("webhook-dispatch-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }

  @Bean(name = "webhookHttpClient")
  public OkHttpClient webhookHttpClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(10))
        .writeTimeout(Duration.ofSeconds(10))
        .connectionPool(new ConnectionPool(50, 5, TimeUnit.MINUTES))
        .addInterceptor(TraceIdPropagator.okHttpInterceptor())
        .build();
  }
}
