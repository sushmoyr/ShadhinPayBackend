package com.shadhinpay.adapters.port;

/**
 * Unified payment status returned by every adapter, decoupled from any vendor-specific state
 * machine.
 *
 * <p>Adapters map vendor codes to one of these values inside their {@code ErrorMapper}. {@code
 * UNKNOWN} signals an unmappable response and forces the orchestrator to retry via {@link
 * PaymentProvider#queryStatus(String, VendorCredentials)}.
 */
public enum VendorStatus {
  /** Initiation accepted by the vendor; customer redirect URL or token returned. */
  INITIATED,
  /** Vendor accepted the request but final state is still resolving. */
  PENDING,
  /** Settlement confirmed by the vendor. */
  COMPLETED,
  /** Vendor returned a terminal failure (declined, insufficient funds, etc.). */
  FAILED,
  /** Customer or vendor cancelled the flow before completion. */
  CANCELLED,
  /** Vendor response could not be mapped; orchestrator must reconcile via {@code queryStatus}. */
  UNKNOWN
}
