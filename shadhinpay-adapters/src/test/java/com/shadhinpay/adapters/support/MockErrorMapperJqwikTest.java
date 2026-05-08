package com.shadhinpay.adapters.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.shadhinpay.common.error.ErrorCode;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class MockErrorMapperJqwikTest {

  private final MockErrorMapper mapper = new MockErrorMapper();

  @Property
  void errorIsNeverNullOrInternalError(@ForAll("vendorCodes") String vendorCode) {
    ErrorCode mapped = mapper.map(vendorCode);
    assertThat(mapped).isNotNull();
    assertThat(mapped).isNotEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  @Provide
  Arbitrary<String> vendorCodes() {
    return net.jqwik.api.Arbitraries.strings()
        .withCharRange('a', 'z')
        .ofMinLength(0)
        .ofMaxLength(20)
        .injectNull(0.1);
  }
}
