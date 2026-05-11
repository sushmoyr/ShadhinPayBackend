package pay.conflux.backend.common.error;

import org.springframework.http.HttpStatus;

public class InvalidOperationStateException extends ApiOperationException {

  public InvalidOperationStateException(String message) {
    super(message, ErrorCode.INVALID_OPERATION_STATE, HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
