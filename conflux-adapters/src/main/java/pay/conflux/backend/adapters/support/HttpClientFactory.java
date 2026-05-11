package pay.conflux.backend.adapters.support;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.common.observability.TraceIdPropagator;

/**
 * Builds and caches one isolated {@link OkHttpClient} per {@link Vendor}.
 *
 * <p>This is the only place in the adapters module that constructs HTTP clients. Per-vendor
 * isolation prevents a slow / hung vendor from exhausting another vendor's connection pool — an
 * explicit non-functional requirement in {@code DOCS/features/adapters/PRD.md} §5.
 *
 * <p>Each client is configured with:
 *
 * <ul>
 *   <li>5 s connect timeout
 *   <li>10 s read / write timeout
 *   <li>50-connection pool with 5-minute keep-alive
 *   <li>{@link TraceIdPropagator#okHttpInterceptor()} so outbound calls carry the inbound request's
 *       trace id for end-to-end log correlation
 * </ul>
 */
@Component
public class HttpClientFactory {

  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  static final Duration READ_WRITE_TIMEOUT = Duration.ofSeconds(10);
  static final int MAX_CONNECTIONS = 50;
  static final Duration KEEP_ALIVE = Duration.ofMinutes(5);

  private final ConcurrentMap<Vendor, OkHttpClient> clients = new ConcurrentHashMap<>();

  /**
   * Returns the (cached) {@link OkHttpClient} for {@code vendor}, creating it on first call.
   *
   * @param vendor target vendor
   * @return isolated client for {@code vendor}
   */
  public OkHttpClient clientFor(Vendor vendor) {
    Objects.requireNonNull(vendor, "vendor must not be null");
    return clients.computeIfAbsent(vendor, ignored -> buildClient());
  }

  private OkHttpClient buildClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT)
        .readTimeout(READ_WRITE_TIMEOUT)
        .writeTimeout(READ_WRITE_TIMEOUT)
        .connectionPool(
            new ConnectionPool(
                MAX_CONNECTIONS, KEEP_ALIVE.toMinutes(), java.util.concurrent.TimeUnit.MINUTES))
        .addInterceptor(TraceIdPropagator.okHttpInterceptor())
        .build();
  }
}
