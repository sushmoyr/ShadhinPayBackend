package pay.conflux.backend.adapters.support;

import org.springframework.stereotype.Component;
import pay.conflux.backend.common.error.ErrorCode;

/** Mock implementation of {@link ErrorMapper}. Handles a canonical set of mock error codes. */
@Component
public class MockErrorMapper implements ErrorMapper {

  @Override
  public ErrorCode map(String vendorCode) {
    if (vendorCode == null) {
      return ErrorCode.MFS_ADAPTER_FAILURE;
    }
    return switch (vendorCode) {
      case "INSUFFICIENT_FUNDS" -> ErrorCode.INSUFFICIENT_FUNDS;
      case "VENDOR_DOWN" -> ErrorCode.VENDOR_DOWN;
      default -> ErrorCode.MFS_ADAPTER_FAILURE;
    };
  }
}
