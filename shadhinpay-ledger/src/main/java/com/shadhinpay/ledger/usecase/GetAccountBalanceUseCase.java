package com.shadhinpay.ledger.usecase;

import com.shadhinpay.common.money.Money;
import java.util.UUID;

/**
 * Returns the current cached balance for an owner's account at a given chart-of-accounts code.
 *
 * <p><b>Caller:</b> the {@code settlement} module when computing payouts, the merchant dashboard
 * when displaying balances, and admin tooling for reconciliation.
 *
 * <p><b>Guarantees:</b>
 *
 * <ul>
 *   <li>Reads the {@code LedgerAccount.balance} cached column rather than summing postings — the
 *       module enforces {@code SUM(postings.amount) == account.balance} via a periodic integrity
 *       job (see {@code DOCS/features/ledger/TECH_SPEC.md} §6).
 *   <li>For sharded system accounts (e.g. {@code ESCROW_0..9}), callers should pass the logical
 *       owner; sharding aggregation is the implementation's responsibility.
 *   <li>Throws (rather than returning {@code null}) when no account exists for the {@code (ownerId,
 *       accountCode)} pair.
 * </ul>
 */
public interface GetAccountBalanceUseCase {

  /**
   * Returns the current balance of the account identified by {@code (ownerId, accountCode)}.
   *
   * @param ownerId merchant or system-account owner; never {@code null}
   * @param accountCode chart-of-accounts code (e.g. {@code "MERCHANT_PAYABLE"}, {@code
   *     "PLATFORM_REVENUE"})
   * @return the current balance; never {@code null}
   */
  Money execute(UUID ownerId, String accountCode);
}
