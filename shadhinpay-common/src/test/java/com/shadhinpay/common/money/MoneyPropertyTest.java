package com.shadhinpay.common.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.Scale;

class MoneyPropertyTest {

  @Property(tries = 200)
  void addThenSubtract_returnsOriginal(
      @ForAll("amounts") BigDecimal a, @ForAll("amounts") BigDecimal b) {
    Money first = Money.of(a, "BDT");
    Money second = Money.of(b, "BDT");

    Money result = first.add(second).subtract(second);

    assertThat(result.amount()).isEqualByComparingTo(first.amount());
  }

  @Property(tries = 200)
  void canonicalForm_alwaysScaleFour(
      @ForAll @BigRange(min = "-1000000000", max = "1000000000") @Scale(8) BigDecimal raw) {
    Money m = Money.of(raw, "BDT");

    assertThat(m.amount().scale()).isEqualTo(4);
  }

  @Property(tries = 200)
  void halfEven_neverDriftsBeyondHalfUnitOfLastPlace(
      @ForAll @BigRange(min = "-100000", max = "100000") @Scale(6) BigDecimal raw) {
    Money m = Money.of(raw, "BDT");

    BigDecimal diff = m.amount().subtract(raw).abs();
    assertThat(diff).isLessThanOrEqualTo(new BigDecimal("0.00005"));
  }

  @Property(tries = 100)
  void negate_isInvolution(
      @ForAll @BigRange(min = "-1000000", max = "1000000") @Scale(4) BigDecimal raw) {
    Money m = Money.of(raw, "BDT");

    assertThat(m.negate().negate().amount()).isEqualByComparingTo(m.amount());
  }

  @Provide
  Arbitrary<BigDecimal> amounts() {
    return Arbitraries.bigDecimals()
        .between(new BigDecimal("-1000000"), new BigDecimal("1000000"))
        .ofScale(6);
  }
}
