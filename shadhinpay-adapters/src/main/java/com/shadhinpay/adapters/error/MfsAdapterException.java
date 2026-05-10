package com.shadhinpay.adapters.error;

import com.shadhinpay.common.error.ApiOperationException;
import com.shadhinpay.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an adapter-level invariant is violated — e.g. no {@code PaymentProvider} bean
 * supports the requested {@link com.shadhinpay.adapters.port.Vendor}, or an HTTP client cannot be
 * constructed for a vendor.
 *
 * <p>Adapters themselves should NOT throw this on vendor-side failures; vendor failures are encoded
 * in {@link com.shadhinpay.adapters.port.VendorResponse} with a populated {@link ErrorCode}. This
 * exception is reserved for misconfiguration / programmer errors that surface to the global {@code
 * GlobalExceptionHandler} as {@code 502 BAD_GATEWAY}.
 */
public class MfsAdapterException extends ApiOperationException {

  public MfsAdapterException(String message) {
    super(message, ErrorCode.MFS_ADAPTER_FAILURE, HttpStatus.BAD_GATEWAY);
  }

  public MfsAdapterException(ErrorCode code, String message) {
    super(message, code, HttpStatus.BAD_GATEWAY);
  }

  public MfsAdapterException(ErrorCode code, String message, Throwable cause) {
    super(message, code, HttpStatus.BAD_GATEWAY);
    this.initCause(cause);
  }
}
