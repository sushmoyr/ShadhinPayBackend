package pay.conflux.backend.adapters.support;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import pay.conflux.backend.adapters.error.MfsAdapterException;
import pay.conflux.backend.adapters.port.PaymentProvider;
import pay.conflux.backend.adapters.port.Vendor;

/**
 * Spring-managed registry that resolves a {@link Vendor} to the unique {@link PaymentProvider} bean
 * that handles it. All discovered providers are injected at startup; lookup is O(n) over a short
 * list (one bean per vendor).
 *
 * <p>Throws {@link MfsAdapterException} when no bean reports {@link
 * PaymentProvider#supports(Vendor)} {@code == true} for the requested vendor — i.e. the platform
 * has been asked to dispatch to a vendor it does not yet implement.
 */
@Component
public class PaymentProviderRegistry {

  private final List<PaymentProvider> providers;

  public PaymentProviderRegistry(List<PaymentProvider> providers) {
    this.providers = List.copyOf(Objects.requireNonNull(providers, "providers must not be null"));
  }

  /**
   * Returns the provider that supports {@code vendor}.
   *
   * @param vendor target vendor
   * @return the matching {@link PaymentProvider}
   * @throws MfsAdapterException if no provider supports {@code vendor}
   */
  public PaymentProvider lookup(Vendor vendor) {
    Objects.requireNonNull(vendor, "vendor must not be null");
    for (PaymentProvider provider : providers) {
      if (provider.supports(vendor)) {
        return provider;
      }
    }
    throw new MfsAdapterException("No PaymentProvider registered for vendor " + vendor);
  }
}
