package pay.conflux.backend.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiOperationExceptionTest {

  @Test
  void resourceNotFound_setsStatusAndCode() {
    ResourceNotFoundException ex = new ResourceNotFoundException("User", 42L);

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    assertThat(ex.getMessage()).contains("User").contains("42");
  }

  @Test
  void duplicateResource_returns409() {
    DuplicateResourceException ex = new DuplicateResourceException("Email", "address", "x@y.z");

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS);
  }

  @Test
  void validation_returns400() {
    ValidationException ex = new ValidationException("bad");

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  void invalidOperationState_returns422() {
    InvalidOperationStateException ex = new InvalidOperationStateException("nope");

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_OPERATION_STATE);
  }

  @Test
  void unauthorized_returns401() {
    UnauthorizedException ex = new UnauthorizedException("auth");

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  void forbidden_returns403() {
    ForbiddenException ex = new ForbiddenException("forbidden");

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
  }
}
