package pay.conflux.backend.paymentcore.entity;

/**
 * Dispatch mode for a transaction. Flows in from the provisioning {@code VendorConfigDescriptor}.
 */
public enum TransactionMode {
  /** Platform-supplied credentials; quota is metered. */
  PARTNER,
  /** Merchant-supplied (encrypted) credentials; quota is skipped. */
  CUSTOM
}
