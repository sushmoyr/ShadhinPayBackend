package com.shadhinpay.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class ApiOperationException extends RuntimeException {

  private final ErrorCode errorCode;
  private final HttpStatus status;

  protected ApiOperationException(String message, ErrorCode errorCode, HttpStatus status) {
    super(message);
    this.errorCode = errorCode;
    this.status = status;
  }

  protected ApiOperationException(String message, ErrorCode errorCode) {
    this(message, errorCode, HttpStatus.BAD_REQUEST);
  }
}
