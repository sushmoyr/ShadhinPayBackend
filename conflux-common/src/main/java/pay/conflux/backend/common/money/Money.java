package pay.conflux.backend.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {

  public static final int SCALE = 4;
  public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

  public Money {
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
    if (currency.isBlank()) {
      throw new IllegalArgumentException("currency must not be blank");
    }
    amount = amount.setScale(SCALE, ROUNDING);
  }

  public static Money of(BigDecimal amount, String currency) {
    return new Money(amount, currency);
  }

  public static Money of(long amount, String currency) {
    return new Money(BigDecimal.valueOf(amount), currency);
  }

  public static Money zero(String currency) {
    return new Money(BigDecimal.ZERO, currency);
  }

  public Money add(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  public Money subtract(Money other) {
    requireSameCurrency(other);
    return new Money(amount.subtract(other.amount), currency);
  }

  public Money multiply(BigDecimal factor) {
    Objects.requireNonNull(factor, "factor must not be null");
    return new Money(amount.multiply(factor), currency);
  }

  public Money negate() {
    return new Money(amount.negate(), currency);
  }

  public boolean isPositive() {
    return amount.signum() > 0;
  }

  public boolean isZero() {
    return amount.signum() == 0;
  }

  public boolean isNegative() {
    return amount.signum() < 0;
  }

  private void requireSameCurrency(Money other) {
    Objects.requireNonNull(other, "other must not be null");
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException(
          "Currency mismatch: " + currency + " vs " + other.currency);
    }
  }
}
