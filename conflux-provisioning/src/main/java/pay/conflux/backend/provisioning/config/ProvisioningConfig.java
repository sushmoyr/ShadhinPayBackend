package pay.conflux.backend.provisioning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class ProvisioningConfig {

  @Bean(name = "provisioningEventExecutor")
  public TaskExecutor provisioningEventExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("prov-evt-");
    executor.initialize();
    return executor;
  }
}
