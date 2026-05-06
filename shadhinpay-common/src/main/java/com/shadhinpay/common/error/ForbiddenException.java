package com.shadhinpay.common.error;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiOperationException {

  public ForbiddenException(String message) {
    super(message, ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
  }
}
