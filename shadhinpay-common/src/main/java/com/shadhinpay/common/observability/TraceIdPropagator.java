package com.shadhinpay.common.observability;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;

public final class TraceIdPropagator {

  private TraceIdPropagator() {}

  public static Interceptor okHttpInterceptor() {
    return new OkHttpTraceIdInterceptor();
  }

  public static ClientHttpRequestInterceptor clientHttpInterceptor() {
    return new SpringClientTraceIdInterceptor();
  }

  static final class OkHttpTraceIdInterceptor implements Interceptor {
    @Override
    @NonNull
    public Response intercept(@NonNull Chain chain) throws IOException {
      Request original = chain.request();
      String traceId = TraceIdContextInitializer.currentTraceId();
      if (traceId == null || traceId.isBlank()) {
        return chain.proceed(original);
      }
      Request augmented =
          original.newBuilder().header(TraceIdFilter.TRACE_ID_HEADER, traceId).build();
      return chain.proceed(augmented);
    }
  }

  static final class SpringClientTraceIdInterceptor implements ClientHttpRequestInterceptor {
    @Override
    @NonNull
    public ClientHttpResponse intercept(
        @NonNull HttpRequest request,
        @NonNull byte[] body,
        @NonNull ClientHttpRequestExecution execution)
        throws IOException {
      String traceId = TraceIdContextInitializer.currentTraceId();
      if (traceId != null && !traceId.isBlank()) {
        request.getHeaders().set(TraceIdFilter.TRACE_ID_HEADER, traceId);
      }
      return execution.execute(request, body);
    }
  }
}
