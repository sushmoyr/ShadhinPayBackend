package pay.conflux.backend.adapters.support;

import pay.conflux.backend.common.error.ErrorCode;

/**
 * Maps vendor-specific error codes into the unified {@link ErrorCode} enumeration. Each adapter
 * provides its own implementation of this interface.
 */
public interface ErrorMapper {

  /**
   * Maps a vendor's native error code to a standard platform error code.
   *
   * @param vendorCode the native error code string returned by the vendor
   * @return the mapped {@link ErrorCode}, never {@code null}. Unknown codes map to {@code
   *     MFS_ADAPTER_FAILURE}.
   */
  ErrorCode map(String vendorCode);
}
