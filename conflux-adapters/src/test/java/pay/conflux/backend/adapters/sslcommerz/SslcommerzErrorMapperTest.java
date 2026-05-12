package pay.conflux.backend.adapters.sslcommerz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pay.conflux.backend.common.error.ErrorCode;

class SslcommerzErrorMapperTest {

  @Test
  void insufficientSubstring_mapsToInsufficientFunds() {
    assertThat(SslcommerzErrorMapper.map("INSUFFICIENT BALANCE"))
        .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
    assertThat(SslcommerzErrorMapper.map("Your balance is insufficient"))
        .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
  }

  @Test
  void invalidCardSubstring_mapsToValidationError() {
    assertThat(SslcommerzErrorMapper.map("Invalid Card Number"))
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  void genericInvalidSubstring_mapsToValidationError() {
    assertThat(SslcommerzErrorMapper.map("Invalid request")).isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  void expiredSubstring_mapsToValidationError() {
    assertThat(SslcommerzErrorMapper.map("Card has expired")).isEqualTo(ErrorCode.VALIDATION_ERROR);
    assertThat(SslcommerzErrorMapper.map("Session will expire soon"))
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  void declinedSubstring_mapsToAdapterFailure() {
    assertThat(SslcommerzErrorMapper.map("Transaction declined by issuer"))
        .isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
  }

  @Test
  void timeoutSubstring_mapsToVendorDown() {
    assertThat(SslcommerzErrorMapper.map("Gateway timeout")).isEqualTo(ErrorCode.VENDOR_DOWN);
    assertThat(SslcommerzErrorMapper.map("Connection timed out")).isEqualTo(ErrorCode.VENDOR_DOWN);
  }

  @Test
  void duplicateSubstring_mapsToResourceAlreadyExists() {
    assertThat(SslcommerzErrorMapper.map("Duplicate transaction id"))
        .isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS);
  }

  @Test
  void unauthorSubstring_mapsToUnauthorized() {
    assertThat(SslcommerzErrorMapper.map("Unauthorized merchant"))
        .isEqualTo(ErrorCode.UNAUTHORIZED);
    assertThat(SslcommerzErrorMapper.map("Unauthorised key")).isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  void cancelSubstring_mapsToAdapterFailure() {
    assertThat(SslcommerzErrorMapper.map("Customer cancelled checkout"))
        .isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
  }

  @Test
  void caseInsensitive() {
    assertThat(SslcommerzErrorMapper.map("INSUFFICIENT")).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
    assertThat(SslcommerzErrorMapper.map("insufficient")).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
    assertThat(SslcommerzErrorMapper.map("InSufficient")).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
  }

  @Test
  void nullAndBlank_mapToAdapterFailure() {
    assertThat(SslcommerzErrorMapper.map(null)).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
    assertThat(SslcommerzErrorMapper.map("")).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
    assertThat(SslcommerzErrorMapper.map("   ")).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
  }

  @Test
  void unmatched_fallsThroughToAdapterFailure() {
    assertThat(SslcommerzErrorMapper.map("totally unexpected wording"))
        .isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
  }
}
