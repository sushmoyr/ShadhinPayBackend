package pay.conflux.backend.adapters.support;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.adapters.port.Vendor;

class HttpClientFactoryTest {

  @Test
  void each_vendor_gets_distinct_client_instance() {
    HttpClientFactory factory = new HttpClientFactory();

    OkHttpClient bkash = factory.clientFor(Vendor.BKASH);
    OkHttpClient nagad = factory.clientFor(Vendor.NAGAD);
    OkHttpClient stripe = factory.clientFor(Vendor.STRIPE);

    assertThat(bkash).isNotSameAs(nagad);
    assertThat(bkash).isNotSameAs(stripe);
    assertThat(nagad).isNotSameAs(stripe);
  }

  @Test
  void same_vendor_returns_cached_instance() {
    HttpClientFactory factory = new HttpClientFactory();

    OkHttpClient first = factory.clientFor(Vendor.MOCK);
    OkHttpClient second = factory.clientFor(Vendor.MOCK);

    assertThat(first).isSameAs(second);
  }

  @Test
  void clients_use_configured_timeouts() {
    HttpClientFactory factory = new HttpClientFactory();

    OkHttpClient client = factory.clientFor(Vendor.MOCK);

    assertThat(client.connectTimeoutMillis())
        .isEqualTo((int) HttpClientFactory.CONNECT_TIMEOUT.toMillis());
    assertThat(client.readTimeoutMillis())
        .isEqualTo((int) HttpClientFactory.READ_WRITE_TIMEOUT.toMillis());
    assertThat(client.writeTimeoutMillis())
        .isEqualTo((int) HttpClientFactory.READ_WRITE_TIMEOUT.toMillis());
  }
}
