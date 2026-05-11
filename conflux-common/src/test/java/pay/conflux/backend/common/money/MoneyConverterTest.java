package pay.conflux.backend.common.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyConverterTest {

  private final MoneyConverter converter = new MoneyConverter();

  @Test
  void toDb_extractsAmount() {
    BigDecimal db = converter.convertToDatabaseColumn(Money.of(new BigDecimal("12.34"), "BDT"));

    assertThat(db).isEqualByComparingTo("12.3400");
  }

  @Test
  void toDb_nullPassesThrough() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
  }

  @Test
  void fromDb_buildsBdtMoney() {
    Money money = converter.convertToEntityAttribute(new BigDecimal("99.50"));

    assertThat(money.currency()).isEqualTo(Currencies.BDT);
    assertThat(money.amount()).isEqualByComparingTo("99.5000");
  }

  @Test
  void fromDb_nullPassesThrough() {
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }
}
