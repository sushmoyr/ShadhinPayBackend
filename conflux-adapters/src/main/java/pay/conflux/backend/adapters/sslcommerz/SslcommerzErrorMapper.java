package pay.conflux.backend.adapters.sslcommerz;

import java.util.Locale;
import pay.conflux.backend.common.error.ErrorCode;

/**
 * Maps SSLCommerz {@code failedreason} / {@code status} strings to the platform's {@link ErrorCode}
 * enum.
 *
 * <p><b>Why substring matching?</b> SSLCommerz does not publish a stable, enumerated list of error
 * codes — their gateway returns free-form English phrases (e.g. {@code "Invalid Card Number"},
 * {@code "INSUFFICIENT BALANCE"}, {@code "Card has expired"}). Substring matching is the only
 * durable hook against a vendor that may change exact wording across releases. Order matters:
 * {@code "invalid card"} is checked before the generic {@code "invalid"} because the former is a
 * strictly more specific signal but both happen to route to {@link ErrorCode#VALIDATION_ERROR}.
 *
 * <p>Unmatched / null / blank inputs fall through to {@link ErrorCode#MFS_ADAPTER_FAILURE} — never
 * {@link ErrorCode#INTERNAL_ERROR}, which is reserved for platform bugs.
 *
 * <p><b>Mapping note:</b> the {@code "duplicate"} substring is routed to {@link
 * ErrorCode#RESOURCE_ALREADY_EXISTS} because the Wave C {@code ErrorCode} enum does not contain a
 * dedicated {@code DUPLICATE_RESOURCE} value; {@code RESOURCE_ALREADY_EXISTS} is its semantic
 * equivalent.
 */
public final class SslcommerzErrorMapper {

  private SslcommerzErrorMapper() {
    // utility class
  }

  /**
   * Returns the {@link ErrorCode} that best describes {@code reason}.
   *
   * @param reason SSLCommerz {@code failedreason}, {@code element[0].status}, or {@code
   *     errorReason}; may be {@code null} or blank
   * @return mapped {@link ErrorCode}; never {@code null}; never {@link ErrorCode#INTERNAL_ERROR}
   */
  public static ErrorCode map(String reason) {
    if (reason == null || reason.isBlank()) {
      return ErrorCode.MFS_ADAPTER_FAILURE;
    }
    String lower = reason.toLowerCase(Locale.ROOT);
    if (lower.contains("insufficient")) {
      return ErrorCode.INSUFFICIENT_FUNDS;
    }
    if (lower.contains("invalid card") || lower.contains("invalid")) {
      return ErrorCode.VALIDATION_ERROR;
    }
    if (lower.contains("expired") || lower.contains("expire")) {
      return ErrorCode.VALIDATION_ERROR;
    }
    if (lower.contains("timeout") || lower.contains("timed out")) {
      return ErrorCode.VENDOR_DOWN;
    }
    if (lower.contains("duplicate")) {
      return ErrorCode.RESOURCE_ALREADY_EXISTS;
    }
    if (lower.contains("unauthor")) {
      return ErrorCode.UNAUTHORIZED;
    }
    if (lower.contains("declined") || lower.contains("cancel")) {
      return ErrorCode.MFS_ADAPTER_FAILURE;
    }
    return ErrorCode.MFS_ADAPTER_FAILURE;
  }
}
