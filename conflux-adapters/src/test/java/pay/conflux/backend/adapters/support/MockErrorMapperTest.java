package pay.conflux.backend.adapters.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pay.conflux.backend.common.error.ErrorCode;

class MockErrorMapperTest {

  private final MockErrorMapper mapper = new MockErrorMapper();

  @Test
  void mapsDocumentedCodes() {
    assertThat(mapper.map("INSUFFICIENT_FUNDS")).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
    assertThat(mapper.map("VENDOR_DOWN")).isEqualTo(ErrorCode.VENDOR_DOWN);
  }

  @Test
  void mapsUnknownCodesToAdapterFailure() {
    assertThat(mapper.map("UNKNOWN_CODE")).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
    assertThat(mapper.map(null)).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
    assertThat(mapper.map("")).isEqualTo(ErrorCode.MFS_ADAPTER_FAILURE);
  }
}
