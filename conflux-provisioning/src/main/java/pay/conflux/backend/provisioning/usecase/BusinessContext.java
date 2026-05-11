package pay.conflux.backend.provisioning.usecase;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable view of the business that owns the API key on an inbound request.
 *
 * <p>Returned by {@link GetBusinessByApiKeyUseCase}. Carries only what callers in other modules
 * need to route work; secrets and adapter credentials are never present.
 *
 * @param businessId stable identifier of the {@code Business} aggregate
 * @param merchantId owning merchant ({@code User}) of the business
 * @param environment {@code "TEST"} or {@code "LIVE"}, derived from the API key prefix
 * @param webhookUrl HTTPS endpoint the merchant has registered for server-to-server callbacks
 */
public record BusinessContext(
    UUID businessId, UUID merchantId, String environment, String webhookUrl) {

  public BusinessContext {
    Objects.requireNonNull(businessId, "businessId must not be null");
    Objects.requireNonNull(merchantId, "merchantId must not be null");
    Objects.requireNonNull(environment, "environment must not be null");
    Objects.requireNonNull(webhookUrl, "webhookUrl must not be null");
  }
}
