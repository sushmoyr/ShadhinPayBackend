package com.shadhinpay.common.observability;

import java.util.UUID;
import java.util.concurrent.Callable;
import org.slf4j.MDC;

public final class TraceIdContextInitializer {

  private TraceIdContextInitializer() {}

  public static String currentTraceId() {
    return MDC.get(TraceIdFilter.MDC_KEY);
  }

  public static void runWithTraceId(Runnable task) {
    runWithTraceId(UUID.randomUUID().toString(), task);
  }

  public static void runWithTraceId(String traceId, Runnable task) {
    String resolved = resolveOrGenerate(traceId);
    String previous = MDC.get(TraceIdFilter.MDC_KEY);
    MDC.put(TraceIdFilter.MDC_KEY, resolved);
    try {
      task.run();
    } finally {
      restore(previous);
    }
  }

  public static <T> T callWithTraceId(Callable<T> task) throws Exception {
    return callWithTraceId(UUID.randomUUID().toString(), task);
  }

  public static <T> T callWithTraceId(String traceId, Callable<T> task) throws Exception {
    String resolved = resolveOrGenerate(traceId);
    String previous = MDC.get(TraceIdFilter.MDC_KEY);
    MDC.put(TraceIdFilter.MDC_KEY, resolved);
    try {
      return task.call();
    } finally {
      restore(previous);
    }
  }

  public static Runnable wrap(Runnable task) {
    String captured = MDC.get(TraceIdFilter.MDC_KEY);
    return () -> {
      String previous = MDC.get(TraceIdFilter.MDC_KEY);
      if (captured != null) {
        MDC.put(TraceIdFilter.MDC_KEY, captured);
      }
      try {
        task.run();
      } finally {
        restore(previous);
      }
    };
  }

  public static <T> Callable<T> wrap(Callable<T> task) {
    String captured = MDC.get(TraceIdFilter.MDC_KEY);
    return () -> {
      String previous = MDC.get(TraceIdFilter.MDC_KEY);
      if (captured != null) {
        MDC.put(TraceIdFilter.MDC_KEY, captured);
      }
      try {
        return task.call();
      } finally {
        restore(previous);
      }
    };
  }

  private static String resolveOrGenerate(String traceId) {
    if (traceId == null || traceId.isBlank()) {
      return UUID.randomUUID().toString();
    }
    try {
      return UUID.fromString(traceId.trim()).toString();
    } catch (IllegalArgumentException e) {
      return UUID.randomUUID().toString();
    }
  }

  private static void restore(String previous) {
    if (previous == null) {
      MDC.remove(TraceIdFilter.MDC_KEY);
    } else {
      MDC.put(TraceIdFilter.MDC_KEY, previous);
    }
  }
}
