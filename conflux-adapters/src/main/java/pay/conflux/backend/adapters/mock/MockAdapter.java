package pay.conflux.backend.adapters.mock;

import org.springframework.stereotype.Component;
import pay.conflux.backend.adapters.port.PaymentProvider;
import pay.conflux.backend.adapters.port.Vendor;
import pay.conflux.backend.adapters.port.VendorCredentials;
import pay.conflux.backend.adapters.port.VendorPaymentRequest;
import pay.conflux.backend.adapters.port.VendorRefundRequest;
import pay.conflux.backend.adapters.port.VendorResponse;
import pay.conflux.backend.adapters.port.VendorStatus;
import pay.conflux.backend.common.error.ErrorCode;

@Component
public class MockAdapter implements PaymentProvider {

  @Override
  public VendorResponse initiate(VendorPaymentRequest request, VendorCredentials creds) {
    String mockOutcome = request.metadata().get("mock_outcome");
    String vendorTrxId = "MOCK-" + request.transactionId();

    if ("success".equals(mockOutcome)) {
      return new VendorResponse(VendorStatus.COMPLETED, vendorTrxId, null, "{\"ok\":true}", null);
    } else if ("fail".equals(mockOutcome)) {
      return new VendorResponse(
          VendorStatus.FAILED,
          vendorTrxId,
          null,
          "{\"err\":\"failed\"}",
          ErrorCode.MFS_ADAPTER_FAILURE);
    } else if ("cancel".equals(mockOutcome)) {
      return new VendorResponse(
          VendorStatus.CANCELLED,
          vendorTrxId,
          null,
          "{\"err\":\"cancelled\"}",
          ErrorCode.MFS_ADAPTER_FAILURE);
    } else if ("insufficient_funds".equals(mockOutcome)) {
      return new VendorResponse(
          VendorStatus.FAILED,
          vendorTrxId,
          null,
          "{\"err\":\"insufficient_funds\"}",
          ErrorCode.INSUFFICIENT_FUNDS);
    } else {
      return new VendorResponse(
          VendorStatus.INITIATED,
          vendorTrxId,
          "https://mock.conflux.local/pay/" + vendorTrxId,
          "{}",
          null);
    }
  }

  @Override
  public VendorResponse queryStatus(String vendorTrxId, VendorCredentials creds) {
    if (vendorTrxId == null) {
      return new VendorResponse(VendorStatus.COMPLETED, null, null, "{}", null);
    }

    // Deterministic rule based on vendorTrxId suffix
    if (vendorTrxId.endsWith("-pending")) {
      return new VendorResponse(VendorStatus.INITIATED, vendorTrxId, null, "{}", null);
    } else if (vendorTrxId.endsWith("-completed")) {
      return new VendorResponse(VendorStatus.COMPLETED, vendorTrxId, null, "{}", null);
    } else if (vendorTrxId.endsWith("-failed")) {
      return new VendorResponse(
          VendorStatus.FAILED, vendorTrxId, null, "{}", ErrorCode.MFS_ADAPTER_FAILURE);
    } else {
      return new VendorResponse(VendorStatus.COMPLETED, vendorTrxId, null, "{}", null);
    }
  }

  /**
   * Returns COMPLETED by default. {@code VendorRefundRequest} is a locked port type with no {@code
   * metadata} field; to preserve a controllable fail path for downstream tests, the {@code
   * mock_refund_outcome=fail} toggle is encoded into the {@code reason} string.
   */
  @Override
  public VendorResponse refund(VendorRefundRequest request, VendorCredentials creds) {
    boolean isFail =
        request.reason() != null && request.reason().contains("mock_refund_outcome=fail");
    if (isFail) {
      return new VendorResponse(
          VendorStatus.FAILED,
          "REFUND-" + request.transactionId(),
          null,
          "{\"err\":\"failed\"}",
          ErrorCode.MFS_ADAPTER_FAILURE);
    }
    return new VendorResponse(
        VendorStatus.COMPLETED, "REFUND-" + request.transactionId(), null, "{}", null);
  }

  @Override
  public boolean supports(Vendor vendor) {
    return vendor == Vendor.MOCK;
  }
}
