package pay.conflux.backend.ledger.usecase.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.ledger.dto.AccountIntegrityRecord;
import pay.conflux.backend.ledger.dto.TrialBalanceReportDto;
import pay.conflux.backend.ledger.entity.LedgerAccount;
import pay.conflux.backend.ledger.repository.LedgerAccountRepository;
import pay.conflux.backend.ledger.repository.PostingRepository;

@UseCase
@RequiredArgsConstructor
public class VerifyTrialBalanceUseCaseImpl implements VerifyTrialBalanceUseCase {

  private final LedgerAccountRepository accountRepository;
  private final PostingRepository postingRepository;

  @Override
  @Transactional(readOnly = true)
  public TrialBalanceReportDto execute() {
    List<LedgerAccount> accounts = accountRepository.findAll();
    List<AccountIntegrityRecord> mismatches = new ArrayList<>();
    BigDecimal globalSum = BigDecimal.ZERO;

    for (LedgerAccount account : accounts) {
      BigDecimal signedSum = postingRepository.sumAmountByAccountId(account.getId());
      if (signedSum == null) {
        signedSum = BigDecimal.ZERO;
      }

      globalSum = globalSum.add(signedSum);

      // Expected balance: for ASSET/EXPENSE/CLEARING (debitSign=1), balance == signedSum
      // For LIABILITY/REVENUE (debitSign=-1), balance == -signedSum
      BigDecimal expectedAmount =
          account.getType().debitSign() > 0 ? signedSum : signedSum.negate();

      Money expectedBalance = new Money(expectedAmount, account.getCurrency());
      Money actualBalance = account.getBalance();

      if (expectedBalance.amount().compareTo(actualBalance.amount()) != 0) {
        BigDecimal delta = actualBalance.amount().subtract(expectedAmount);
        mismatches.add(
            new AccountIntegrityRecord(
                account.getId(),
                account.getCode(),
                account.getOwnerId(),
                expectedAmount.toPlainString(),
                actualBalance.amount().toPlainString(),
                delta.toPlainString()));
      }
    }

    boolean globalSumZero = globalSum.compareTo(BigDecimal.ZERO) == 0;

    return new TrialBalanceReportDto(globalSumZero, List.copyOf(mismatches), Instant.now());
  }
}
