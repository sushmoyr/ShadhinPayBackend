package pay.conflux.backend.adapters.sslcommerz;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import pay.conflux.backend.common.error.ErrorCode;

/**
 * Exhaustiveness invariant: every possible vendor reason string maps to a non-null {@link
 * ErrorCode}, and never to {@link ErrorCode#INTERNAL_ERROR} (which is reserved for platform bugs).
 */
class SslcommerzErrorMapperJqwikTest {

  @Property
  void mappingIsExhaustiveAndNeverInternalError(@ForAll("vendorReasons") String reason) {
    ErrorCode mapped = SslcommerzErrorMapper.map(reason);
    assertThat(mapped).isNotNull();
    assertThat(mapped).isNotEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  @Provide
  Arbitrary<String> vendorReasons() {
    return Arbitraries.strings()
        .withCharRange(' ', '~')
        .ofMinLength(0)
        .ofMaxLength(60)
        .injectNull(0.1);
  }
}
