package pay.conflux.backend.adapters.bkash;

import pay.conflux.backend.adapters.support.ErrorMapper;
import pay.conflux.backend.common.error.ErrorCode;

/**
 * Maps bKash Tokenized Checkout v1.2 {@code statusCode} strings to the platform's {@link ErrorCode}
 * enum.
 *
 * <p>Status codes are drawn from the bKash merchant onboarding PDF ({@code
 * bkash.devarif.me/doc.pdf} and community SDKs); the public developer portal does NOT publish an
 * exhaustive enumeration. The default → {@link ErrorCode#MFS_ADAPTER_FAILURE} fallthrough is the
 * durable hook for any code not in this table.
 *
 * <table>
 *   <caption>Documented mappings</caption>
 *   <tr><th>bKash code</th><th>Meaning</th><th>ErrorCode</th></tr>
 *   <tr><td>{@code 0000}</td><td>Success</td><td>(no error; handled separately)</td></tr>
 *   <tr><td>{@code 2001}</td><td>Validation error</td><td>{@link ErrorCode#VALIDATION_ERROR}</td></tr>
 *   <tr><td>{@code 2002}</td><td>Token expired / invalid</td><td>{@link ErrorCode#MFS_ADAPTER_FAILURE}
 *     (adapter retries once)</td></tr>
 *   <tr><td>{@code 2003}</td><td>Auth failed</td><td>{@link ErrorCode#UNAUTHORIZED}</td></tr>
 *   <tr><td>{@code 2023}</td><td>Insufficient funds</td><td>{@link ErrorCode#INSUFFICIENT_FUNDS}</td></tr>
 *   <tr><td>{@code 2024}</td><td>Daily limit exceeded</td><td>{@link ErrorCode#INSUFFICIENT_FUNDS}</td></tr>
 *   <tr><td>{@code 2056}</td><td>Duplicate transaction</td><td>{@link ErrorCode#RESOURCE_ALREADY_EXISTS}</td></tr>
 *   <tr><td>{@code 503}</td><td>Service unavailable</td><td>{@link ErrorCode#VENDOR_DOWN}</td></tr>
 * </table>
 */
public final class BkashErrorMapper implements ErrorMapper {

  private static final BkashErrorMapper INSTANCE = new BkashErrorMapper();

  private BkashErrorMapper() {
    // singleton
  }

  /** Returns the shared instance for callers that need {@link ErrorMapper}. */
  public static BkashErrorMapper instance() {
    return INSTANCE;
  }

  /** Static mapping entrypoint; see {@link #map(String)}. */
  public static ErrorCode mapCode(String bkashStatusCode) {
    if (bkashStatusCode == null || bkashStatusCode.isBlank()) {
      return ErrorCode.MFS_ADAPTER_FAILURE;
    }
    return switch (bkashStatusCode) {
      case "2001" -> ErrorCode.VALIDATION_ERROR;
      case "2002" -> ErrorCode.MFS_ADAPTER_FAILURE;
      case "2003" -> ErrorCode.UNAUTHORIZED;
      case "2023", "2024" -> ErrorCode.INSUFFICIENT_FUNDS;
      case "2056" -> ErrorCode.RESOURCE_ALREADY_EXISTS;
      case "503" -> ErrorCode.VENDOR_DOWN;
      default -> ErrorCode.MFS_ADAPTER_FAILURE;
    };
  }

  @Override
  public ErrorCode map(String bkashStatusCode) {
    return mapCode(bkashStatusCode);
  }
}
