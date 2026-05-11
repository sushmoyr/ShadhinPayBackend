package pay.conflux.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import pay.conflux.backend.common.error.ErrorCode;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResultMeta {

  private boolean success;
  private String message;
  private ErrorCode errorCode;
  private Instant timestamp;

  public static ApiResultMeta success() {
    ApiResultMeta meta = new ApiResultMeta();
    meta.setSuccess(true);
    meta.setTimestamp(Instant.now());
    return meta;
  }

  public static ApiResultMeta success(String message) {
    ApiResultMeta meta = success();
    meta.setMessage(message);
    return meta;
  }

  public static ApiResultMeta failure(String message, ErrorCode errorCode) {
    ApiResultMeta meta = new ApiResultMeta();
    meta.setSuccess(false);
    meta.setMessage(message);
    meta.setErrorCode(errorCode);
    meta.setTimestamp(Instant.now());
    return meta;
  }
}
