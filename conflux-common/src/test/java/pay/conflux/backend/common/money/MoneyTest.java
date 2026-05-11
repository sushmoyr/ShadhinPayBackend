package pay.conflux.backend.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void canonicalConstructor_normalisesScaleToFourHalfEven() {
    Money m = new Money(new BigDecimal("1.123456789"), "BDT");

    assertThat(m.amount().scale()).isEqualTo(4);
    assertThat(m.amount()).isEqualByComparingTo("1.1235");
  }

  @Test
  void halfEven_roundsToNearestEvenAtBoundary() {
    Money up = new Money(new BigDecimal("1.23455"), "BDT");
    Money down = new Money(new BigDecimal("1.23445"), "BDT");

    assertThat(up.amount()).isEqualByComparingTo("1.2346");
    assertThat(down.amount()).isEqualByComparingTo("1.2344");
  }

  @Test
  void add_sumsAmounts() {
    Money a = Money.of(10, "BDT");
    Money b = Money.of(5, "BDT");

    assertThat(a.add(b).amount()).isEqualByComparingTo("15.0000");
  }

  @Test
  void subtract_subtractsAmounts() {
    Money a = Money.of(10, "BDT");
    Money b = Money.of(3, "BDT");

    assertThat(a.subtract(b).amount()).isEqualByComparingTo("7.0000");
  }

  @Test
  void multiply_appliesFactorWithRescale() {
    Money m = Money.of(new BigDecimal("2.5000"), "BDT").multiply(new BigDecimal("3"));

    assertThat(m.amount()).isEqualByComparingTo("7.5000");
  }

  @Test
  void negate_invertsSign() {
    assertThat(Money.of(10, "BDT").negate().amount()).isEqualByComparingTo("-10.0000");
  }

  @Test
  void signHelpers_workForPositiveZeroAndNegative() {
    assertThat(Money.of(1, "BDT").isPositive()).isTrue();
    assertThat(Money.zero("BDT").isZero()).isTrue();
    assertThat(Money.of(-1, "BDT").isNegative()).isTrue();
  }

  @Test
  void currencyMismatch_throws() {
    Money bdt = Money.of(1, "BDT");
    Money usd = Money.of(1, "USD");

    assertThatThrownBy(() -> bdt.add(usd)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> bdt.subtract(usd)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blankCurrency_rejected() {
    assertThatThrownBy(() -> new Money(BigDecimal.ONE, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void scaleConstantMatchesSpec() {
    assertThat(Money.SCALE).isEqualTo(4);
    assertThat(Money.ROUNDING).isEqualTo(RoundingMode.HALF_EVEN);
  }
}
