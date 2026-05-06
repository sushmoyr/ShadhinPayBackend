package com.shadhinpay.adapters.port;

/**
 * Identifier for every MFS / card payment provider supported by the platform.
 *
 * <p>Used by {@link PaymentProvider#supports(Vendor)} so the {@code PaymentProviderRegistry} can
 * pick the right adapter for a transaction. {@code MOCK} is reserved for development and sandbox.
 */
public enum Vendor {
  BKASH,
  NAGAD,
  ROCKET,
  UPAY,
  PATHAO,
  MCASH,
  STRIPE,
  MOCK
}
