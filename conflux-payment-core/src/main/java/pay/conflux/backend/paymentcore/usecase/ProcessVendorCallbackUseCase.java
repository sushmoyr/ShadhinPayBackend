package pay.conflux.backend.paymentcore.usecase;

import java.util.Map;
import java.util.UUID;

/**
 * Handles the customer-return ping from an MFS vendor.
 *
 * <p>The handler is the only path (apart from the reconciliation poller, which delegates here) that
 * may finalize a {@code Transaction} to {@code COMPLETED}, {@code FAILED}, or {@code CANCELLED}
 * after the initiation phase. It is strictly idempotent — re-running the same callback for an
 * already-terminal transaction is a no-op (no duplicate events, no duplicate journal entries).
 */
public interface ProcessVendorCallbackUseCase {

  /**
   * Resolves the target transaction from {@code callbackParams}, queries the vendor for the
   * authoritative status, and transitions the {@code Transaction} accordingly.
   *
   * @param vendor vendor identifier (e.g. {@code "MOCK"}, {@code "BKASH"}) — selects the adapter
   * @param callbackParams vendor-specific params (form-urlencoded or JSON-decoded). For {@code
   *     MOCK}, expects {@code mock_trx_id} to locate the row via {@code vendor_transaction_id}.
   * @return descriptor with the resolved transaction id and its post-callback status
   */
  ProcessVendorCallbackResult execute(String vendor, Map<String, String> callbackParams);

  /**
   * Re-entry point used by the reconciliation scheduler when the {@code (vendor, vendorTrxId)} pair
   * is already known — bypasses the param-parsing branch.
   *
   * @param transactionId target transaction
   * @return the resulting state
   */
  ProcessVendorCallbackResult resolveByTransactionId(UUID transactionId);
}
