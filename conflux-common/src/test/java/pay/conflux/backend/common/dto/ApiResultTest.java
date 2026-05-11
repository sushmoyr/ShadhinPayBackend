package pay.conflux.backend.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pay.conflux.backend.common.error.ErrorCode;

class ApiResultTest {

  @Test
  void ok_wrapsDataWithSuccessMeta() {
    ResponseEntity<ApiResult<String>> response = ApiResult.ok("payload");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ApiResult<String> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.data()).isEqualTo("payload");
    assertThat(body.meta().isSuccess()).isTrue();
    assertThat(body.meta().getErrorCode()).isNull();
    assertThat(body.meta().getTimestamp()).isNotNull();
    assertThat(body.pagination()).isNull();
  }

  @Test
  void ok_withPage_wrapsContentAndPagination() {
    Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 4L);

    ResponseEntity<ApiResult<List<String>>> response = ApiResult.ok(page);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ApiResult<List<String>> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.data()).containsExactly("a", "b");
    assertThat(body.meta().isSuccess()).isTrue();
    assertThat(body.pagination()).isNotNull();
    assertThat(body.pagination().page()).isZero();
    assertThat(body.pagination().size()).isEqualTo(2);
    assertThat(body.pagination().totalElements()).isEqualTo(4L);
    assertThat(body.pagination().totalPages()).isEqualTo(2);
  }

  @Test
  void created_returns201WithSuccessMessage() {
    ResponseEntity<ApiResult<String>> response = ApiResult.created("new");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    ApiResult<String> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.data()).isEqualTo("new");
    assertThat(body.meta().getMessage()).isEqualTo("Resource created");
    assertThat(body.meta().isSuccess()).isTrue();
  }

  @Test
  void okVoid_returnsEmptyDataWithSuccessMeta() {
    ResponseEntity<ApiResult<Void>> response = ApiResult.ok();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ApiResult<Void> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.data()).isNull();
    assertThat(body.meta().isSuccess()).isTrue();
  }

  @Test
  void error_setsStatusMessageAndErrorCode() {
    ResponseEntity<ApiResult<Void>> response =
        ApiResult.error(HttpStatus.NOT_FOUND, "missing", ErrorCode.RESOURCE_NOT_FOUND);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    ApiResult<Void> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.data()).isNull();
    assertThat(body.meta().isSuccess()).isFalse();
    assertThat(body.meta().getMessage()).isEqualTo("missing");
    assertThat(body.meta().getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void validationError_returns400WithFieldErrorsAsData() {
    Map<String, String> errors = Map.of("name", "must not be blank", "age", "must be positive");

    ResponseEntity<ApiResult<Map<String, String>>> response = ApiResult.validationError(errors);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    ApiResult<Map<String, String>> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.data()).containsAllEntriesOf(errors);
    assertThat(body.meta().isSuccess()).isFalse();
    assertThat(body.meta().getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
  }
}
