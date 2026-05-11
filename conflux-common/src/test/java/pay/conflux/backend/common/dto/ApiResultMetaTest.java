package pay.conflux.backend.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pay.conflux.backend.common.error.ErrorCode;

class ApiResultMetaTest {

  @Test
  void success_setsSuccessTrueAndTimestamp() {
    ApiResultMeta meta = ApiResultMeta.success();

    assertThat(meta.isSuccess()).isTrue();
    assertThat(meta.getMessage()).isNull();
    assertThat(meta.getErrorCode()).isNull();
    assertThat(meta.getTimestamp()).isNotNull();
  }

  @Test
  void successWithMessage_attachesMessage() {
    ApiResultMeta meta = ApiResultMeta.success("hello");

    assertThat(meta.isSuccess()).isTrue();
    assertThat(meta.getMessage()).isEqualTo("hello");
  }

  @Test
  void failure_setsErrorCodeAndMessage() {
    ApiResultMeta meta = ApiResultMeta.failure("nope", ErrorCode.UNAUTHORIZED);

    assertThat(meta.isSuccess()).isFalse();
    assertThat(meta.getMessage()).isEqualTo("nope");
    assertThat(meta.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    assertThat(meta.getTimestamp()).isNotNull();
  }
}
