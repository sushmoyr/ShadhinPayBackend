package pay.conflux.backend.adapters.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.adapters.error.MfsAdapterException;
import pay.conflux.backend.adapters.port.PaymentProvider;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;
import pay.conflux.backend.adapters.port.VendorPaymentRequest;
import pay.conflux.backend.adapters.port.VendorRefundRequest;
import pay.conflux.backend.adapters.port.VendorResponse;
import pay.conflux.backend.adapters.port.VendorStatus;

class PaymentProviderRegistryTest {

  @Test
  void lookup_returns_provider_supporting_vendor() {
    StubProvider mock = new StubProvider(Vendor.MOCK);
    StubProvider stripe = new StubProvider(Vendor.STRIPE);

    PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of(mock, stripe));

    assertThat(registry.lookup(Vendor.MOCK)).isSameAs(mock);
    assertThat(registry.lookup(Vendor.STRIPE)).isSameAs(stripe);
  }

  @Test
  void lookup_throws_when_no_provider_supports_vendor() {
    PaymentProviderRegistry registry =
        new PaymentProviderRegistry(List.of(new StubProvider(Vendor.MOCK)));

    assertThatThrownBy(() -> registry.lookup(Vendor.BKASH))
        .isInstanceOf(MfsAdapterException.class)
        .hasMessageContaining("BKASH");
  }

  @Test
  void lookup_throws_when_no_providers_registered() {
    PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of());

    assertThatThrownBy(() -> registry.lookup(Vendor.STRIPE))
        .isInstanceOf(MfsAdapterException.class);
  }

  private static final class StubProvider implements PaymentProvider {
    private final Vendor vendor;

    StubProvider(Vendor vendor) {
      this.vendor = vendor;
    }

    @Override
    public VendorResponse initiate(VendorPaymentRequest request, VendorCredentials creds) {
      return new VendorResponse(VendorStatus.INITIATED, "stub", null, null, null);
    }

    @Override
    public VendorResponse queryStatus(String vendorTrxId, VendorCredentials creds) {
      return new VendorResponse(VendorStatus.PENDING, vendorTrxId, null, null, null);
    }

    @Override
    public VendorResponse refund(VendorRefundRequest request, VendorCredentials creds) {
      return new VendorResponse(VendorStatus.COMPLETED, "stub", null, null, null);
    }

    @Override
    public boolean supports(Vendor v) {
      return v == this.vendor;
    }
  }
}
