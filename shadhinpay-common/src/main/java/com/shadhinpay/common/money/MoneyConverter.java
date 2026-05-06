package com.shadhinpay.common.money;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

@Converter(autoApply = false)
public class MoneyConverter implements AttributeConverter<Money, BigDecimal> {

  @Override
  public BigDecimal convertToDatabaseColumn(Money attribute) {
    return attribute == null ? null : attribute.amount();
  }

  @Override
  public Money convertToEntityAttribute(BigDecimal dbData) {
    return dbData == null ? null : new Money(dbData, Currencies.BDT);
  }
}
