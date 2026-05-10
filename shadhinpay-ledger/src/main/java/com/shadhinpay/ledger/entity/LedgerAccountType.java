package com.shadhinpay.ledger.entity;

import com.shadhinpay.common.money.Money;

public enum LedgerAccountType {
  ASSET(1),
  LIABILITY(-1),
  REVENUE(-1),
  EXPENSE(1),
  CLEARING(1);

  private final int debitSign;

  LedgerAccountType(int debitSign) {
    this.debitSign = debitSign;
  }

  public int debitSign() {
    return debitSign;
  }

  /**
   * Calculates the new balance after applying a posting.
   *
   * @param currentBalance the current balance
   * @param postingType DEBIT or CREDIT
   * @param amount the absolute posting amount
   * @return the new balance
   */
  public Money applyDelta(Money currentBalance, PostingType postingType, Money amount) {
    if (postingType == PostingType.DEBIT) {
      return debitSign > 0 ? currentBalance.add(amount) : currentBalance.subtract(amount);
    } else { // CREDIT
      return debitSign > 0 ? currentBalance.subtract(amount) : currentBalance.add(amount);
    }
  }
}
