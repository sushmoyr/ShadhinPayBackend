package pay.conflux.backend.common.error;

import org.springframework.http.HttpStatus;

public class ValidationException extends ApiOperationException {

  public ValidationException(String message) {
    super(message, ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST);
  }

  public ValidationException(String message, Throwable cause) {
    super(message, cause, ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST);
  }
}
