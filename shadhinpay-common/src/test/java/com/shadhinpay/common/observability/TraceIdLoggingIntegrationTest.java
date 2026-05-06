package com.shadhinpay.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.OutputStreamAppender;
import jakarta.servlet.FilterChain;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdLoggingIntegrationTest {

  private OutputStreamAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender;
  private ByteArrayOutputStream buffer;
  private Logger captured;

  @BeforeEach
  void setupAppender() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(context);
    encoder.setPattern("[%X{traceId:-}] %m%n");
    encoder.start();

    buffer = new ByteArrayOutputStream();
    appender = new OutputStreamAppender<>();
    appender.setContext(context);
    appender.setEncoder(encoder);
    appender.setOutputStream(buffer);
    appender.start();

    captured = context.getLogger("com.shadhinpay.test.tracecapture");
    captured.setLevel(Level.INFO);
    captured.addAppender(appender);
  }

  @AfterEach
  void teardown() {
    captured.detachAppender(appender);
    appender.stop();
    MDC.clear();
  }

  @Test
  void logEmittedDuringRequest_containsTraceIdPrefix() throws Exception {
    String existing = UUID.randomUUID().toString();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
    request.addHeader(TraceIdFilter.TRACE_ID_HEADER, existing);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain =
        (req, res) ->
            LoggerFactory.getLogger("com.shadhinpay.test.tracecapture").info("processing request");

    new TraceIdFilter().doFilter(request, response, chain);

    String output = buffer.toString();
    assertThat(output).contains("[" + existing + "] processing request");
  }
}
