package pay.conflux.backend.adapters.bkash;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pay.conflux.backend.common.error.ErrorCode;

class BkashErrorMapperTest {

  @Test
  void code2001_mapsToValidationError() {
    assertThat(BkashErrorMapper.mapCode("2001")).isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  void code2002_mapsToAdapterFailure_adapterHandlesRetry() {
    assertThat(BkashErrorMapper.mapCode("2002")).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
  }

  @Test
  void code2003_mapsToUnauthorized() {
    assertThat(BkashErrorMapper.mapCode("2003")).isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  void code2023_mapsToInsufficientFunds() {
    assertThat(BkashErrorMapper.mapCode("2023")).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
  }

  @Test
  void code2024_mapsToInsufficientFunds() {
    assertThat(BkashErrorMapper.mapCode("2024")).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
  }

  @Test
  void code2056_mapsToResourceAlreadyExists() {
    assertThat(BkashErrorMapper.mapCode("2056")).isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS);
  }

  @Test
  void code503_mapsToVendorDown() {
    assertThat(BkashErrorMapper.mapCode("503")).isEqualTo(ErrorCode.VENDOR_DOWN);
  }

  @Test
  void unknownCode_fallsThroughToAdapterFailure() {
    assertThat(BkashErrorMapper.mapCode("9999")).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
  }

  @Test
  void nullAndBlank_mapToAdapterFailure() {
    assertThat(BkashErrorMapper.mapCode(null)).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
    assertThat(BkashErrorMapper.mapCode("")).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
    assertThat(BkashErrorMapper.mapCode("   ")).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
  }

  @Test
  void instanceErrorMapper_delegatesToStatic() {
    assertThat(BkashErrorMapper.instance().map("2023")).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
    assertThat(BkashErrorMapper.instance().map("nope")).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
  }
}
