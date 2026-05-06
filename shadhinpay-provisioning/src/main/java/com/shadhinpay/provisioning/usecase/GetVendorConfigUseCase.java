package com.shadhinpay.provisioning.usecase;

import java.util.UUID;

/**
 * Resolves the configured {@link VendorConfigDescriptor} for a {@code (businessId, vendor)} pair.
 *
 * <p><b>Caller:</b> {@code payment-core} during the pre-flight phase of {@code
 * InitiatePaymentUseCase}, immediately before dispatching to the MFS adapter.
 *
 * <p><b>Guarantees:</b>
 *
 * <ul>
 *   <li>The returned descriptor contains only opaque credential references; encrypted secrets are
 *       resolved later by the adapter layer.
 *   <li>Throws (rather than returning {@code null}) when no active vendor configuration exists for
 *       the business.
 *   <li>May be backed by a Redis cache; cache eviction follows {@code VendorConfig} updates.
 * </ul>
 */
public interface GetVendorConfigUseCase {

  /**
   * Resolves the active vendor configuration for a business.
   *
   * @param businessId business that owns the configuration
   * @param vendor vendor identifier (e.g. {@code "BKASH"}); see {@link VendorConfigDescriptor}
   * @return the active {@link VendorConfigDescriptor}; never {@code null}
   */
  VendorConfigDescriptor execute(UUID businessId, String vendor);
}
