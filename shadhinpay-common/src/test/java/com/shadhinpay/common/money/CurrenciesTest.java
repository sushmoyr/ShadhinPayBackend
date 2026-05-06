package com.shadhinpay.common.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class CurrenciesTest {

  @Test
  void bdtConstantIsBDT() {
    assertThat(Currencies.BDT).isEqualTo("BDT");
  }

  @Test
  void privateConstructor_invokable() throws Exception {
    Constructor<Currencies> ctor = Currencies.class.getDeclaredConstructor();
    assertThat(Modifier.isPrivate(ctor.getModifiers())).isTrue();
    ctor.setAccessible(true);
    assertThat(ctor.newInstance()).isNotNull();
  }
}
