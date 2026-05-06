package com.shadhinpay.common.error;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiOperationException {

  public UnauthorizedException(String message) {
    super(message, ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
  }
}
