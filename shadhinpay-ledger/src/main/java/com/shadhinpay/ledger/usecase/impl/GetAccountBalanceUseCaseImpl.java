package com.shadhinpay.ledger.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.common.money.Money;
import com.shadhinpay.ledger.entity.LedgerAccount;
import com.shadhinpay.ledger.repository.LedgerAccountRepository;
import com.shadhinpay.ledger.usecase.GetAccountBalanceUseCase;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class GetAccountBalanceUseCaseImpl implements GetAccountBalanceUseCase {

  private final LedgerAccountRepository ledgerAccountRepository;

  private static final List<String> SYSTEM_ACCOUNT_CODES =
      List.of("ESCROW", "PLATFORM_REVENUE", "VENDOR_PAYABLE");
  private static final String DEFAULT_CURRENCY = "BDT";

  /**
   * Returns the balance for the given account. Note on read-after-write: for sharded system
   * accounts, summing the balances across shards may be eventually consistent during rapid updates,
   * as updates land on different rows.
   */
  @Override
  @Transactional(readOnly = true)
  public Money execute(UUID ownerId, String accountCode) {
    if (SYSTEM_ACCOUNT_CODES.contains(accountCode)) {
      List<LedgerAccount> shards =
          ledgerAccountRepository.findByCodeAndCurrency(accountCode, DEFAULT_CURRENCY);
      if (shards.isEmpty()) {
        throw new ResourceNotFoundException("System account not found: " + accountCode);
      }
      Money total = Money.zero(DEFAULT_CURRENCY);
      for (LedgerAccount shard : shards) {
        total = total.add(shard.getBalance());
      }
      return total;
    } else {
      LedgerAccount account =
          ledgerAccountRepository
              .findByOwnerIdAndCodeAndShardIdAndCurrency(ownerId, accountCode, 0, DEFAULT_CURRENCY)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Merchant account not found: " + accountCode + " for owner " + ownerId));
      return account.getBalance();
    }
  }
}
