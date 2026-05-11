package pay.conflux.backend.common.error;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiOperationException {

  public ResourceNotFoundException(String resource, Object id) {
    super(
        resource + " not found with id: " + id, ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
  }

  public ResourceNotFoundException(String message) {
    super(message, ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
  }
}
