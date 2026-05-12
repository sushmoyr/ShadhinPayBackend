package pay.conflux.backend.adapters.bkash;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import pay.conflux.backend.common.error.ErrorCode;

/**
 * Exhaustiveness invariant: every possible bKash {@code statusCode} string maps to a non-null
 * {@link ErrorCode}, and never to {@link ErrorCode#INTERNAL_ERROR} (which is reserved for platform
 * bugs).
 */
class BkashErrorMapperJqwikTest {

  @Property(tries = 1000)
  void mappingIsExhaustiveAndNeverInternalError(@ForAll("bkashCodes") String code) {
    ErrorCode mapped = BkashErrorMapper.mapCode(code);
    assertThat(mapped).isNotNull();
    assertThat(mapped).isNotEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  @Provide
  Arbitrary<String> bkashCodes() {
    return Arbitraries.strings().alpha().numeric().ofMinLength(0).ofMaxLength(8).injectNull(0.1);
  }
}
