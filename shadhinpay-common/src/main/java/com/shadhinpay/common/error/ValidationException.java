package com.shadhinpay.common.error;

import org.springframework.http.HttpStatus;

public class ValidationException extends ApiOperationException {

  public ValidationException(String message) {
    super(message, ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST);
  }
}
