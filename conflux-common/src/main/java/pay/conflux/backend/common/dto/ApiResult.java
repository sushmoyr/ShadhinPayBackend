package pay.conflux.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pay.conflux.backend.common.error.ErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResult<T>(T data, ApiResultMeta meta, PaginationInfo pagination) {

  public static <T> ResponseEntity<ApiResult<T>> ok(T data) {
    return ResponseEntity.ok(new ApiResult<>(data, ApiResultMeta.success(), null));
  }

  public static <T> ResponseEntity<ApiResult<List<T>>> ok(Page<T> page) {
    return ResponseEntity.ok(
        new ApiResult<>(page.getContent(), ApiResultMeta.success(), PaginationInfo.from(page)));
  }

  public static <T> ResponseEntity<ApiResult<T>> created(T data) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new ApiResult<>(data, ApiResultMeta.success("Resource created"), null));
  }

  public static ResponseEntity<ApiResult<Void>> ok() {
    return ResponseEntity.ok(new ApiResult<>(null, ApiResultMeta.success(), null));
  }

  public static <T> ResponseEntity<ApiResult<T>> error(
      HttpStatus status, String message, ErrorCode code) {
    return ResponseEntity.status(status)
        .body(new ApiResult<>(null, ApiResultMeta.failure(message, code), null));
  }

  public static ResponseEntity<ApiResult<Map<String, String>>> validationError(
      Map<String, String> errors) {
    return ResponseEntity.badRequest()
        .body(
            new ApiResult<>(
                errors,
                ApiResultMeta.failure("Validation failed", ErrorCode.VALIDATION_ERROR),
                null));
  }
}
