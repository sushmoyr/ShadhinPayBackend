package pay.conflux.backend.common.observability;

import java.util.Collections;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

@Configuration
public class LoggingConfig {

  public static final String CONSOLE_PATTERN =
      "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} "
          + "%clr(%5p) "
          + "%clr([${spring.application.name:conflux},%X{traceId:-}]){yellow} "
          + "%clr(---){faint} %clr([%15.15t]){faint} "
          + "%clr(%-40.40logger{39}){cyan} %clr(:){faint} %m%n%wEx";

  static final String FILE_PATTERN =
      "%d{yyyy-MM-dd HH:mm:ss.SSS} "
          + "%5p "
          + "[${spring.application.name:conflux},%X{traceId:-}] "
          + "--- [%t] %-40.40logger{39} : %m%n%wEx";

  public static final String PROPERTY_SOURCE_NAME = "confluxLoggingDefaults";

  public static class TraceAwareLoggingPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(
        ConfigurableEnvironment environment, SpringApplication application) {
      if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
        return;
      }
      PropertySource<?> defaults =
          new MapPropertySource(
              PROPERTY_SOURCE_NAME,
              Collections.unmodifiableMap(
                  java.util.Map.of(
                      "logging.pattern.console", CONSOLE_PATTERN,
                      "logging.pattern.file", FILE_PATTERN)));
      environment.getPropertySources().addLast(defaults);
    }
  }
}
