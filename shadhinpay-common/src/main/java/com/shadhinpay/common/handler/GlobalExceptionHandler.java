package com.shadhinpay.common.handler;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.common.error.ApiOperationException;
import com.shadhinpay.common.error.ErrorCode;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.common.observability.TraceIdFilter;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResult<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
    log.warn("Resource not found [traceId={}]: {}", traceId(), ex.getMessage());
    return ApiResult.error(HttpStatus.NOT_FOUND, ex.getMessage(), ex.getErrorCode());
  }

  @ExceptionHandler(ApiOperationException.class)
  public ResponseEntity<ApiResult<Void>> handleApiOperation(ApiOperationException ex) {
    if (ex.getStatus().is5xxServerError()) {
      log.error("API operation server error [traceId={}]: {}", traceId(), ex.getMessage(), ex);
    } else {
      log.warn("API operation error [traceId={}]: {}", traceId(), ex.getMessage());
    }
    return ApiResult.error(ex.getStatus(), ex.getMessage(), ex.getErrorCode());
  }

  @ExceptionHandler(AuthorizationDeniedException.class)
  public ResponseEntity<ApiResult<Void>> handleAuthorizationDenied(
      AuthorizationDeniedException ex) {
    log.warn("Authorization denied [traceId={}]: {}", traceId(), ex.getMessage());
    return ApiResult.error(HttpStatus.FORBIDDEN, "Access denied", ErrorCode.FORBIDDEN);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResult<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
    log.warn("Data integrity violation [traceId={}]: {}", traceId(), ex.getMessage());
    return ApiResult.error(
        HttpStatus.CONFLICT, "Resource conflict", ErrorCode.RESOURCE_ALREADY_EXISTS);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
    log.warn("Validation failed [traceId={}]: {}", traceId(), errors);
    return new ResponseEntity<>(
        ApiResult.validationError(errors).getBody(), HttpStatus.BAD_REQUEST);
  }

  private static String traceId() {
    String value = MDC.get(TraceIdFilter.MDC_KEY);
    return value == null ? "-" : value;
  }
}
