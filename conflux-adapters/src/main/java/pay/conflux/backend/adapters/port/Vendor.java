package pay.conflux.backend.adapters.port;

/**
 * Identifier for every MFS / card payment provider supported by the platform.
 *
 * <p>Used by {@link PaymentProvider#supports(Vendor)} so the {@code PaymentProviderRegistry} can
 * pick the right adapter for a transaction. {@code MOCK} is reserved for development and sandbox.
 *
 * <p>{@code BKASH}, {@code NAGAD}, and {@code ROCKET} are Bangladeshi Mobile Financial Service
 * (MFS) wallets. {@code SSLCOMMERZ} is a Bangladeshi card-aggregator / payment gateway (no MFS
 * wallet of its own) and is therefore categorically distinct from the MFS entries above; treat it
 * as a card channel for routing and reconciliation purposes.
 */
public enum Vendor {
  BKASH,
  NAGAD,
  ROCKET,
  UPAY,
  PATHAO,
  MCASH,
  SSLCOMMERZ,
  STRIPE,
  MOCK
}
