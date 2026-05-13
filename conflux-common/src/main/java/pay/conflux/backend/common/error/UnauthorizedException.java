package pay.conflux.backend.common.error;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiOperationException {

  public UnauthorizedException(String message) {
    super(message, ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
  }

  public UnauthorizedException(String message, Throwable cause) {
    super(message, cause, ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
  }
}
