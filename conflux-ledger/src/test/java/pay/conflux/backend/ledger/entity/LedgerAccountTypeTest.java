package pay.conflux.backend.ledger.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pay.conflux.backend.common.money.Money;

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

  @Test
  void testApplyDeltaForAllTypes() {
    Money balance = Money.of(100, "BDT");
    Money amount = Money.of(50, "BDT");

    // Assets/Expenses: Increase on DEBIT, decrease on CREDIT
    assertThat(LedgerAccountType.ASSET.applyDelta(balance, PostingType.DEBIT, amount).amount())
        .isEqualByComparingTo("150");
    assertThat(LedgerAccountType.ASSET.applyDelta(balance, PostingType.CREDIT, amount).amount())
        .isEqualByComparingTo("50");

    assertThat(LedgerAccountType.EXPENSE.applyDelta(balance, PostingType.DEBIT, amount).amount())
        .isEqualByComparingTo("150");
    assertThat(LedgerAccountType.EXPENSE.applyDelta(balance, PostingType.CREDIT, amount).amount())
        .isEqualByComparingTo("50");

    // Liabilities/Revenues: Decrease on DEBIT, increase on CREDIT
    assertThat(LedgerAccountType.LIABILITY.applyDelta(balance, PostingType.DEBIT, amount).amount())
        .isEqualByComparingTo("50");
    assertThat(LedgerAccountType.LIABILITY.applyDelta(balance, PostingType.CREDIT, amount).amount())
        .isEqualByComparingTo("150");

    assertThat(LedgerAccountType.REVENUE.applyDelta(balance, PostingType.DEBIT, amount).amount())
        .isEqualByComparingTo("50");
    assertThat(LedgerAccountType.REVENUE.applyDelta(balance, PostingType.CREDIT, amount).amount())
        .isEqualByComparingTo("150");

    // Clearing: Increase on DEBIT, decrease on CREDIT
    assertThat(LedgerAccountType.CLEARING.applyDelta(balance, PostingType.DEBIT, amount).amount())
        .isEqualByComparingTo("150");
    assertThat(LedgerAccountType.CLEARING.applyDelta(balance, PostingType.CREDIT, amount).amount())
        .isEqualByComparingTo("50");
  }
}
