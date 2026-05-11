package pay.conflux.backend.common.error;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends ApiOperationException {

  public DuplicateResourceException(String resource, String field, Object value) {
    super(
        resource + " already exists with " + field + ": " + value,
        ErrorCode.RESOURCE_ALREADY_EXISTS,
        HttpStatus.CONFLICT);
  }

  public DuplicateResourceException(String message) {
    super(message, ErrorCode.RESOURCE_ALREADY_EXISTS, HttpStatus.CONFLICT);
  }
}
