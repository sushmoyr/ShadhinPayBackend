package com.shadhinpay.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {

  public static final String TRACE_ID_HEADER = "X-ShadhinPay-Trace-ID";
  public static final String MDC_KEY = "traceId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String traceId = resolveTraceId(request.getHeader(TRACE_ID_HEADER));
    boolean mdcSet = false;
    try {
      MDC.put(MDC_KEY, traceId);
      mdcSet = true;
    } catch (RuntimeException e) {
      log.warn("Failed to set MDC trace id; continuing without MDC", e);
    }
    response.setHeader(TRACE_ID_HEADER, traceId);
    try {
      chain.doFilter(request, response);
    } finally {
      if (mdcSet) {
        MDC.remove(MDC_KEY);
      }
    }
  }

  private static String resolveTraceId(String headerValue) {
    if (headerValue == null || headerValue.isBlank()) {
      return UUID.randomUUID().toString();
    }
    try {
      return UUID.fromString(headerValue.trim()).toString();
    } catch (IllegalArgumentException e) {
      String fresh = UUID.randomUUID().toString();
      log.warn(
          "Received malformed {} header '{}'; replacing with fresh id {}",
          TRACE_ID_HEADER,
          headerValue,
          fresh);
      return fresh;
    }
  }
}
