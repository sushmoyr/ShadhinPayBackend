package com.shadhinpay.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class LoggingConfigTest {

  @Test
  void consolePatternEmbedsTraceIdMdc() {
    assertThat(LoggingConfig.CONSOLE_PATTERN).contains("%X{traceId");
  }

  @Test
  void filePatternEmbedsTraceIdMdc() {
    assertThat(LoggingConfig.FILE_PATTERN).contains("%X{traceId");
  }

  @Test
  void postProcessor_addsConsolePatternProperty() {
    MockEnvironment environment = new MockEnvironment();
    new LoggingConfig.TraceAwareLoggingPostProcessor()
        .postProcessEnvironment(environment, new SpringApplication());

    var source = environment.getPropertySources().get(LoggingConfig.PROPERTY_SOURCE_NAME);
    assertThat(source).isNotNull();
    assertThat(source.getProperty("logging.pattern.console"))
        .isEqualTo(LoggingConfig.CONSOLE_PATTERN);
    assertThat(source.getProperty("logging.pattern.file")).isEqualTo(LoggingConfig.FILE_PATTERN);
  }

  @Test
  void postProcessor_isIdempotent() {
    MockEnvironment environment = new MockEnvironment();
    int sizeBefore = environment.getPropertySources().size();
    LoggingConfig.TraceAwareLoggingPostProcessor processor =
        new LoggingConfig.TraceAwareLoggingPostProcessor();
    processor.postProcessEnvironment(environment, new SpringApplication());
    int sizeAfterFirst = environment.getPropertySources().size();
    processor.postProcessEnvironment(environment, new SpringApplication());
    int sizeAfterSecond = environment.getPropertySources().size();

    assertThat(sizeAfterFirst).isEqualTo(sizeBefore + 1);
    assertThat(sizeAfterSecond).isEqualTo(sizeAfterFirst);
  }
}
