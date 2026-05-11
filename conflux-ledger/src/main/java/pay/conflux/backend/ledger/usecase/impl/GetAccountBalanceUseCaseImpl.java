package pay.conflux.backend.ledger.usecase.impl;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.ledger.entity.LedgerAccount;
import pay.conflux.backend.ledger.repository.LedgerAccountRepository;
import pay.conflux.backend.ledger.usecase.GetAccountBalanceUseCase;

@UseCase
@RequiredArgsConstructor
public class GetAccountBalanceUseCaseImpl implements GetAccountBalanceUseCase {

  private final LedgerAccountRepository ledgerAccountRepository;

  private static final List<String> SYSTEM_ACCOUNT_CODES =
      List.of("ESCROW", "PLATFORM_REVENUE", "VENDOR_PAYABLE");
  private static final String DEFAULT_CURRENCY = "BDT";

  /**
   * Returns the balance for the given account.
   *
   * <p><b>Convention:</b> {@code ownerId} must be {@code null} when {@code accountCode} is one of
   * the sharded system accounts ({@code ESCROW}, {@code PLATFORM_REVENUE}, {@code VENDOR_PAYABLE});
   * for those, the impl aggregates {@code SUM(balance)} across all 10 shards. For per-merchant
   * codes (e.g. {@code MERCHANT_PAYABLE}) {@code ownerId} is the merchant id. The locked Javadoc on
   * the interface predates the system-account split — treat this comment as authoritative.
   *
   * <p>Read-after-write caveat: for sharded system accounts, the SUM may lag during rapid updates
   * because writes land on different rows.
   */
  @Override
  @Transactional(readOnly = true)
  public Money execute(UUID ownerId, String accountCode) {
    if (SYSTEM_ACCOUNT_CODES.contains(accountCode)) {
      List<LedgerAccount> shards =
          ledgerAccountRepository.findByCodeAndCurrency(accountCode, DEFAULT_CURRENCY);
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
