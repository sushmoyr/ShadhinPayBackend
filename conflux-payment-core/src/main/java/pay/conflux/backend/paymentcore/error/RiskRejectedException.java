package pay.conflux.backend.paymentcore.error;

import org.springframework.http.HttpStatus;
import pay.conflux.backend.common.error.ApiOperationException;
import pay.conflux.backend.common.error.ErrorCode;

/**
 * Thrown when the risk engine returns {@code BLOCK} or fails open (fail-CLOSED policy). Maps to
 * HTTP 403 with {@link ErrorCode#RISK_REJECTED}. The orchestrator never leaks the rule reason to
 * the end customer — only the high-level category survives the boundary.
 */
public class RiskRejectedException extends ApiOperationException {

  public RiskRejectedException(String message) {
    super(message, ErrorCode.RISK_REJECTED, HttpStatus.FORBIDDEN);
  }
}
