package com.shadhinpay.ledger.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.shadhinpay.common.money.Money;
import org.junit.jupiter.api.Test;

class LedgerAccountTypeTest {

  @Test
  void testSignConventionRoundTrip() {
    Money balance = Money.zero("BDT");
    Money amount = Money.of(100, "BDT");

    for (LedgerAccountType type : LedgerAccountType.values()) {
      Money afterDebit = type.applyDelta(balance, PostingType.DEBIT, amount);
      Money afterCredit = type.applyDelta(afterDebit, PostingType.CREDIT, amount);

      assertThat(afterCredit.amount()).isEqualByComparingTo(balance.amount());
    }
  }
}
