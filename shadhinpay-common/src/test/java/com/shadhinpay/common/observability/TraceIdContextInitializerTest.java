package com.shadhinpay.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

class TraceIdContextInitializerTest {

  @AfterEach
  void clear() {
    MDC.clear();
  }

  @Test
  void runWithTraceId_setsAndClearsMdc() {
    String id = UUID.randomUUID().toString();
    AtomicReference<String> seen = new AtomicReference<>();

    TraceIdContextInitializer.runWithTraceId(id, () -> seen.set(MDC.get(TraceIdFilter.MDC_KEY)));

    assertThat(seen.get()).isEqualTo(id);
    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void runWithTraceId_generatesIdWhenAbsent() {
    AtomicReference<String> seen = new AtomicReference<>();

    TraceIdContextInitializer.runWithTraceId(() -> seen.set(MDC.get(TraceIdFilter.MDC_KEY)));

    assertThat(seen.get()).isNotNull();
    assertThat(UUID.fromString(seen.get())).isNotNull();
  }

  @Test
  void callWithTraceId_returnsValueAndClearsMdc() throws Exception {
    String id = UUID.randomUUID().toString();

    String result =
        TraceIdContextInitializer.callWithTraceId(id, () -> MDC.get(TraceIdFilter.MDC_KEY));

    assertThat(result).isEqualTo(id);
    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void runWithTraceId_restoresPreviousValue() {
    String outer = UUID.randomUUID().toString();
    String inner = UUID.randomUUID().toString();
    MDC.put(TraceIdFilter.MDC_KEY, outer);

    TraceIdContextInitializer.runWithTraceId(
        inner, () -> assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isEqualTo(inner));

    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isEqualTo(outer);
  }

  @Test
  void invalidTraceIdReplacedWithFresh() {
    AtomicReference<String> seen = new AtomicReference<>();
    TraceIdContextInitializer.runWithTraceId(
        "garbage", () -> seen.set(MDC.get(TraceIdFilter.MDC_KEY)));
    assertThat(seen.get()).isNotEqualTo("garbage");
    assertThat(UUID.fromString(seen.get())).isNotNull();
  }

  @Test
  void callWithTraceId_propagatesException() {
    assertThatThrownBy(
            () ->
                TraceIdContextInitializer.callWithTraceId(
                    () -> {
                      throw new IllegalStateException("nope");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void wrap_propagatesMdcAcrossTaskExecutor() throws Exception {
    String id = UUID.randomUUID().toString();
    MDC.put(TraceIdFilter.MDC_KEY, id);

    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
    AtomicReference<String> seen = new AtomicReference<>();
    CompletableFuture<Void> done = new CompletableFuture<>();
    executor.execute(
        TraceIdContextInitializer.wrap(
            () -> {
              seen.set(MDC.get(TraceIdFilter.MDC_KEY));
              done.complete(null);
            }));
    done.get();

    assertThat(seen.get()).isEqualTo(id);
  }

  @Test
  void wrap_callable_propagatesMdcToOtherThread() throws Exception {
    String id = UUID.randomUUID().toString();
    MDC.put(TraceIdFilter.MDC_KEY, id);

    var pool = Executors.newSingleThreadExecutor();
    try {
      String result =
          pool.submit(TraceIdContextInitializer.wrap(() -> MDC.get(TraceIdFilter.MDC_KEY))).get();
      assertThat(result).isEqualTo(id);
    } finally {
      pool.shutdown();
    }
  }

  @Test
  void currentTraceId_returnsMdcValue() {
    assertThat(TraceIdContextInitializer.currentTraceId()).isNull();
    String id = UUID.randomUUID().toString();
    MDC.put(TraceIdFilter.MDC_KEY, id);
    assertThat(TraceIdContextInitializer.currentTraceId()).isEqualTo(id);
  }
}
